package com.aditya.minsearch.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.search.application.DocumentIndexingService;
import com.aditya.minsearch.search.application.SearchIndexStore;
import com.aditya.minsearch.search.application.SearchIndexStore.Mutation;
import com.aditya.minsearch.search.domain.SearchDocument;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers(disabledWithoutDocker = true)
class JdbcSearchIndexStoreTest {
  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  static DriverManagerDataSource dataSource;
  JdbcTemplate jdbc;
  SearchIndexStore store;

  @BeforeAll
  static void schema() {
    dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V4__durable_search_index.sql"),
            new ClassPathResource("db/migration/V5__index_outcome_outbox.sql"))
        .execute(dataSource);
  }

  @BeforeEach
  void setup() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE search_index_documents,index_outbox");
    var factory = new ProxyFactory(new JdbcSearchIndexStore(jdbc, JsonMapper.builder().build()));
    factory.addAdvice(
        new TransactionInterceptor(
            new DataSourceTransactionManager(dataSource),
            new AnnotationTransactionAttributeSource()));
    store = (SearchIndexStore) factory.getProxy();
  }

  @Test
  void storesOnlyNewerVersionsAndRecoversSearchStateAndDeletionTombstones() {
    UUID id = UUID.randomUUID();
    var metadata = new SearchDocument(id, UUID.randomUUID(), "en", "txt", Instant.now());
    var first =
        new Mutation(
            UUID.randomUUID(),
            id,
            1,
            false,
            metadata,
            List.of(new TextToken("distributed", 0, 0, 11)));
    long revision = store.save(first).orElseThrow();
    assertThat(store.save(first)).isEmpty();
    assertThat(store.load()).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM index_outbox", Integer.class)).isOne();

    var restarted = new DocumentIndexingService(store, "/unused-during-recovery");
    restarted.recover();
    assertThat(restarted.current().generation().generation()).isEqualTo(revision);
    assertThat(restarted.current().documents()).containsEntry(id, metadata);
    assertThat(restarted.current().generation().postingsFor("distributed").postings()).hasSize(1);

    long deletedRevision =
        store.save(new Mutation(UUID.randomUUID(), id, 2, true, metadata, List.of())).orElseThrow();
    assertThat(deletedRevision).isGreaterThan(revision);
    assertThat(store.save(first)).isEmpty();
    restarted.recover();
    assertThat(restarted.current().generation().document(id).deleted()).isTrue();
    assertThat(restarted.current().documents()).doesNotContainKey(id);
    assertThat(restarted.current().generation().postingsFor("distributed").postings()).isEmpty();
  }

  @Test
  void projectionAndCompletionOutboxCommitAtomically() {
    jdbc.execute(
        "ALTER TABLE index_outbox ADD CONSTRAINT reject_test_outcome CHECK (false) NOT VALID");
    try {
      UUID id = UUID.randomUUID();
      var mutation =
          new Mutation(
              UUID.randomUUID(),
              id,
              1,
              false,
              new SearchDocument(id, UUID.randomUUID(), "en", "txt", Instant.now()),
              List.of());
      assertThatThrownBy(() -> store.save(mutation))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(store.load()).isEmpty();
    } finally {
      jdbc.execute("ALTER TABLE index_outbox DROP CONSTRAINT reject_test_outcome");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void relayWaitsForPublicationAndRetriesFailedSends() {
    UUID id = UUID.randomUUID();
    store.save(
        new Mutation(
            UUID.randomUUID(),
            id,
            1,
            false,
            new SearchDocument(id, UUID.randomUUID(), "en", "txt", Instant.now()),
            List.of()));
    var indexing = new DocumentIndexingService(store, "/unused");
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var relay = new IndexOutcomeRelay(jdbc, kafka, indexing);
    relay.publishPending();
    verifyNoInteractions(kafka);
    indexing.recover();
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
    relay.publishPending();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM index_outbox WHERE published_at IS NULL AND attempts=1",
                Integer.class))
        .isOne();
    jdbc.update("UPDATE index_outbox SET next_attempt_at=now()");
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    relay.publishPending();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM index_outbox WHERE published_at IS NOT NULL", Integer.class))
        .isOne();
  }

  @Test
  void recordsFailureIdempotentlyWithoutOverridingDurableSuccess() {
    UUID id = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    store.save(
        new Mutation(
            UUID.randomUUID(),
            id,
            1,
            false,
            new SearchDocument(id, owner, "en", "txt", Instant.now()),
            List.of()));
    var outbox = new IndexOutcomeOutbox(jdbc, JsonMapper.builder().build());
    var event =
        new com.aditya.minsearch.shared.domain.event.DomainEvent(
            UUID.randomUUID(), "document.uploaded", Instant.now(), "{}");
    var first =
        new com.aditya.minsearch.shared.domain.event.DocumentIndexPayload(
            1, id, owner, 1, "sample.txt", "checksum", "en", "TXT", Instant.now());
    outbox.recordFailure(event, first, "Late failure");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM index_outbox WHERE event_type='index.failed'", Integer.class))
        .isZero();
    var newer =
        new com.aditya.minsearch.shared.domain.event.DocumentIndexPayload(
            1, id, owner, 2, "sample.txt", "checksum", "en", "TXT", Instant.now());
    outbox.recordFailure(event, newer, "Document content failed checksum validation.");
    outbox.recordFailure(event, newer, "Document content failed checksum validation.");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM index_outbox WHERE event_type='index.failed'", Integer.class))
        .isOne();
  }
}

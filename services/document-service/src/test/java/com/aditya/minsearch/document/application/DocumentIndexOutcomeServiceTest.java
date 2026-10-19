package com.aditya.minsearch.document.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.document.domain.storage.DocumentStorage;
import com.aditya.minsearch.document.infrastructure.storage.LocalFilesystemDocumentStorage;
import com.aditya.minsearch.shared.domain.event.IndexOutcomePayload;
import com.aditya.minsearch.shared.domain.event.IndexOutcomePayload.Outcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class DocumentIndexOutcomeServiceTest {
  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  static DriverManagerDataSource dataSource;
  @TempDir Path root;
  JdbcTemplate jdbc;
  DocumentIndexOutcomeService outcomes;
  UUID id;

  @BeforeAll
  static void schema() {
    dataSource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V1__document_schema.sql"),
            new ClassPathResource("db/migration/V2__index_outcomes_and_cleanup.sql"))
        .execute(dataSource);
  }

  @BeforeEach
  void setup() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE document_cleanup_jobs, processed_events, documents CASCADE");
    outcomes = transactional(new DocumentIndexOutcomeService(jdbc));
    id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO documents(id,owner_id,title,original_filename,storage_key,media_type,file_type,
            size_bytes,checksum,language,status,current_version,created_at,updated_at)
        VALUES (?,?,'title','sample.txt','sample.txt','text/plain','TXT',10,'checksum','en','UPLOADED',1,now(),now())
        """,
        id,
        UUID.randomUUID());
  }

  @Test
  void appliesEachOutcomeOnceAndNeverDowngradesSuccessfulOrNewerVersions() {
    UUID event = UUID.randomUUID();
    outcomes.accept(event, outcome(1, Outcome.INDEXED, null));
    outcomes.accept(event, outcome(1, Outcome.INDEXED, null));
    outcomes.accept(UUID.randomUUID(), outcome(1, Outcome.INDEX_FAILED, "Late failure"));
    assertThat(status()).isEqualTo("INDEXED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class))
        .isEqualTo(2);

    jdbc.update("UPDATE documents SET current_version=2,status='UPLOADED' WHERE id=?", id);
    outcomes.accept(UUID.randomUUID(), outcome(1, Outcome.INDEXED, null));
    assertThat(status()).isEqualTo("UPLOADED");
    outcomes.accept(
        UUID.randomUUID(),
        outcome(2, Outcome.INDEX_FAILED, "Document content failed checksum validation."));
    assertThat(status()).isEqualTo("INDEX_FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT indexing_error FROM documents WHERE id=?", String.class, id))
        .contains("checksum");
    outcomes.accept(UUID.randomUUID(), outcome(2, Outcome.INDEXED, null));
    assertThat(status()).isEqualTo("INDEXED");
    assertThat(
            jdbc.queryForObject(
                "SELECT indexing_error FROM documents WHERE id=?", String.class, id))
        .isNull();
  }

  @Test
  void createsCleanupOnlyForTheConfirmedCurrentDeletedVersionAndRetriesIoFailures()
      throws Exception {
    Files.writeString(root.resolve("sample.txt"), "document content");
    DocumentStorage storage = spy(new LocalFilesystemDocumentStorage(root));
    var cleanup = transactional(new DocumentCleanupService(jdbc, storage, Clock.systemUTC()));
    jdbc.update(
        "UPDATE documents SET current_version=3,status='DELETE_PENDING',deleted_at=now() WHERE"
            + " id=?",
        id);
    outcomes.accept(UUID.randomUUID(), outcome(2, Outcome.DELETED, null));
    outcomes.accept(UUID.randomUUID(), outcome(3, Outcome.INDEXED, null));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM document_cleanup_jobs", Integer.class))
        .isZero();
    cleanup.clean(id, 3);
    assertThat(root.resolve("sample.txt")).exists();

    UUID event = UUID.randomUUID();
    outcomes.accept(event, outcome(3, Outcome.DELETED, null));
    outcomes.accept(event, outcome(3, Outcome.DELETED, null));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM document_cleanup_jobs", Integer.class))
        .isOne();
    doThrow(new IOException("temporary storage failure"))
        .doCallRealMethod()
        .when(storage)
        .delete("sample.txt");
    cleanup.clean(id, 3);
    assertThat(root.resolve("sample.txt")).exists();
    assertThat(status()).isEqualTo("DELETE_PENDING");
    assertThat(jdbc.queryForObject("SELECT attempts FROM document_cleanup_jobs", Integer.class))
        .isOne();
    jdbc.update("UPDATE document_cleanup_jobs SET next_attempt_at=now()");
    cleanup.clean(id, 3);
    cleanup.clean(id, 3);
    assertThat(root.resolve("sample.txt")).doesNotExist();
    assertThat(status()).isEqualTo("DELETED");
    verify(storage, times(2)).delete("sample.txt");
  }

  @Test
  void obsoleteCleanupCannotDeleteTheCurrentFile() throws Exception {
    Files.writeString(root.resolve("sample.txt"), "current content");
    jdbc.update(
        "INSERT INTO document_cleanup_jobs(document_id,document_version,storage_key) VALUES"
            + " (?,1,'sample.txt')",
        id);
    jdbc.update("UPDATE documents SET current_version=2 WHERE id=?", id);
    var storage = spy(new LocalFilesystemDocumentStorage(root));
    transactional(new DocumentCleanupService(jdbc, storage, Clock.systemUTC())).clean(id, 1);
    verifyNoInteractions(storage);
    assertThat(root.resolve("sample.txt")).exists();
    assertThat(status()).isEqualTo("UPLOADED");
  }

  @Test
  void cleanupCanRecoverAfterFileDeletionBeforeDatabaseCommit() {
    jdbc.update(
        "UPDATE documents SET current_version=2,status='DELETE_PENDING',deleted_at=now() WHERE"
            + " id=?",
        id);
    outcomes.accept(UUID.randomUUID(), outcome(2, Outcome.DELETED, null));
    var storage = new LocalFilesystemDocumentStorage(root);
    transactional(new DocumentCleanupService(jdbc, storage, Clock.systemUTC())).clean(id, 2);
    assertThat(status()).isEqualTo("DELETED");
  }

  @Test
  void rollsBackStatusIfRecordingTheProcessedEventFails() {
    jdbc.execute(
        "ALTER TABLE processed_events ADD CONSTRAINT reject_test_event CHECK (false) NOT VALID");
    try {
      assertThatThrownBy(
              () -> outcomes.accept(UUID.randomUUID(), outcome(1, Outcome.INDEXED, null)))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(status()).isEqualTo("UPLOADED");
    } finally {
      jdbc.execute("ALTER TABLE processed_events DROP CONSTRAINT reject_test_event");
    }
  }

  private IndexOutcomePayload outcome(long version, Outcome outcome, String error) {
    return new IndexOutcomePayload(
        1, id, version, outcome == Outcome.INDEX_FAILED ? 0 : 10, outcome, error);
  }

  private String status() {
    return jdbc.queryForObject("SELECT status FROM documents WHERE id=?", String.class, id);
  }

  @SuppressWarnings("unchecked")
  private <T> T transactional(T target) {
    var factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(
        new TransactionInterceptor(
            new DataSourceTransactionManager(dataSource),
            new AnnotationTransactionAttributeSource()));
    return (T) factory.getProxy();
  }
}

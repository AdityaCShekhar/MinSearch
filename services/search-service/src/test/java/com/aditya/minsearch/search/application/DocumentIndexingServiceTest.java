package com.aditya.minsearch.search.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentIndexingServiceTest {
  @TempDir Path root;
  private final UUID documentId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();

  @Test
  void indexesUpdatesAndDeletesWithoutApplyingDuplicateOrOlderVersions() throws Exception {
    MemoryStore store = new MemoryStore();
    DocumentIndexingService index = new DocumentIndexingService(store, root.toString());
    DocumentIndexPayload uploaded = payload(1, "en", "distributed systems are useful");
    DomainEvent first = event("document.uploaded");
    index.apply(first, uploaded);
    assertThat(search(index, "\"distributed systems\"", ownerId).total()).isOne();
    assertThat(search(index, "distributed", UUID.randomUUID()).total()).isZero();
    assertThat(index.current().generationForOwner(ownerId).postings()).containsKey("distributed");
    assertThat(index.current().generationForOwner(UUID.randomUUID()).postings()).isEmpty();
    long revision = index.current().generation().generation();
    index.apply(first, uploaded);
    assertThat(index.current().generation().generation()).isEqualTo(revision);

    DocumentIndexPayload updated = payload(2, "fr", "distributed systems have changed");
    index.apply(event("document.updated"), updated);
    assertThat(search(index, "changed language:fr", ownerId).total()).isOne();
    assertThat(search(index, "useful", ownerId).total()).isZero();
    index.apply(event("document.updated"), uploaded);
    assertThat(search(index, "changed", ownerId).total()).isOne();

    index.apply(event("document.deleted"), withVersion(updated, 3));
    index.apply(first, uploaded);
    assertThat(search(index, "distributed", ownerId).total()).isZero();
    assertThat(index.current().generation().document(documentId).deleted()).isTrue();
    assertThat(store.load()).hasSize(1);
  }

  @Test
  void recoversTokensMetadataAndTombstonesWithoutReadingSourceFiles() throws Exception {
    MemoryStore store = new MemoryStore();
    DocumentIndexingService index = new DocumentIndexingService(store, root.toString());
    DocumentIndexPayload payload = payload(1, "en", "distributed systems");
    index.apply(event("document.uploaded"), payload);
    long revision = index.current().generation().generation();
    Files.delete(root.resolve("sample.txt"));
    DocumentIndexingService restarted = new DocumentIndexingService(store, root.toString());
    restarted.recover();
    assertThat(search(restarted, "distributed", ownerId).total()).isOne();
    assertThat(restarted.current().generation().generation()).isEqualTo(revision);
    restarted.apply(event("document.deleted"), withVersion(payload, 2));
    DocumentIndexingService afterDeletion = new DocumentIndexingService(store, root.toString());
    afterDeletion.recover();
    afterDeletion.apply(event("document.uploaded"), payload);
    assertThat(search(afterDeletion, "distributed", ownerId).total()).isZero();
  }

  @Test
  void doesNotPublishWhenPersistenceFailsAndCanRetryTheEvent() throws Exception {
    SearchIndexStore store = mock(SearchIndexStore.class);
    when(store.save(any()))
        .thenThrow(new IllegalStateException("database unavailable"))
        .thenReturn(OptionalLong.of(7));
    DocumentIndexingService index = new DocumentIndexingService(store, root.toString());
    DocumentIndexPayload payload = payload(1, "en", "distributed systems");
    DomainEvent event = event("document.uploaded");
    assertThatThrownBy(() -> index.apply(event, payload)).isInstanceOf(IllegalStateException.class);
    assertThat(index.current().generation().documentCount()).isZero();
    index.apply(event, payload);
    assertThat(search(index, "distributed", ownerId).total()).isOne();
    assertThat(index.current().generation().generation()).isEqualTo(7);
  }

  @Test
  void rejectsChangedContentAndEscapingStorageKeysBeforeSaving() throws Exception {
    SearchIndexStore store = mock(SearchIndexStore.class);
    DocumentIndexingService index = new DocumentIndexingService(store, root.toString());
    DocumentIndexPayload payload = payload(1, "en", "original content");
    Files.writeString(root.resolve("sample.txt"), "tampered content");
    assertThatThrownBy(() -> index.apply(event("document.uploaded"), payload))
        .isInstanceOf(java.io.IOException.class)
        .hasMessageContaining("checksum");
    DocumentIndexPayload escaping =
        new DocumentIndexPayload(
            1,
            documentId,
            ownerId,
            1,
            "../outside.txt",
            payload.checksum(),
            "en",
            "TXT",
            Instant.now());
    assertThatThrownBy(() -> index.apply(event("document.uploaded"), escaping))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(store);
  }

  private SearchPage search(DocumentIndexingService index, String query, UUID owner) {
    var snapshot = index.current();
    return new SearchService()
        .search(query, snapshot.generation(), snapshot.documents(), owner, 0, 20);
  }

  @Test
  void concurrentOutOfOrderVersionsConvergeAndDeletionCannotBeResurrected() throws Exception {
    MemoryStore store = new MemoryStore();
    DocumentIndexingService index = new DocumentIndexingService(store, root.toString());
    DocumentIndexPayload source = payload(1, "en", "distributed systems");
    var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      List<java.util.concurrent.Future<?>> jobs = new ArrayList<>();
      for (int version = 1; version <= 40; version++) {
        var next = withVersion(source, version);
        jobs.add(
            pool.submit(
                () -> {
                  start.await();
                  index.apply(event("document.updated"), next);
                  return null;
                }));
      }
      jobs.add(
          pool.submit(
              () -> {
                start.await();
                index.apply(event("document.deleted"), withVersion(source, 41));
                return null;
              }));
      start.countDown();
      for (var job : jobs) job.get(10, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(index.current().generation().document(documentId).documentVersion()).isEqualTo(41);
      assertThat(index.current().generation().document(documentId).deleted()).isTrue();
      assertThat(index.current().documents()).isEmpty();
      assertThat(index.current().generation().postings()).isEmpty();
      index.recover();
      assertThat(index.current().generation().document(documentId).deleted()).isTrue();
    } finally {
      start.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void shutdownDrainsActivePublicationAndReadersRetainACompleteSnapshot() throws Exception {
    MemoryStore memory = new MemoryStore();
    SearchIndexStore store = mock(SearchIndexStore.class);
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    when(store.save(any()))
        .thenAnswer(
            call -> {
              SearchIndexStore.Mutation mutation = call.getArgument(0);
              if (mutation.version() == 2) {
                entered.countDown();
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                  throw new IllegalStateException("Timed out");
              }
              return memory.save(mutation);
            });
    DocumentIndexingService index = new DocumentIndexingService(store, root.toString());
    var workers=new com.aditya.minsearch.shared.infrastructure.WorkerCoordinator(java.time.Duration.ofSeconds(10));
    workers.start(); index.workers(workers);
    index.apply(event("document.uploaded"), payload(1, "en", "original content"));
    var retained = index.current();
    var updated = payload(2, "fr", "replacement content");
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var job =
          pool.submit(
              () -> {
                index.apply(event("document.updated"), updated);
                return null;
              });
      assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      assertThat(index.current()).isSameAs(retained);
      assertThat(search(index, "original language:en", ownerId).total()).isOne();
      workers.onApplicationEvent(null);
      var shutdown=pool.submit((Runnable) workers::stop);
      assertThatThrownBy(() -> index.apply(event("document.updated"), updated)).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
      assertThat(shutdown.isDone()).isFalse();
      release.countDown();
      job.get(10, java.util.concurrent.TimeUnit.SECONDS);
      shutdown.get(10, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(workers.activeCount()).isZero();
      assertThat(search(index, "replacement language:fr", ownerId).total()).isOne();
      assertThat(retained.generation().postings())
          .containsKey("original")
          .doesNotContainKey("replacement");
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  private DocumentIndexPayload payload(long version, String language, String text)
      throws Exception {
    Files.writeString(root.resolve("sample.txt"), text);
    String checksum =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    return new DocumentIndexPayload(
        1,
        documentId,
        ownerId,
        version,
        "sample.txt",
        checksum,
        language,
        "TXT",
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test void failedStartupStreamNeverPublishesPartiallyRecoveredGeneration() throws Exception {
    var memory=new MemoryStore();
    var store=mock(SearchIndexStore.class);
    when(store.save(any())).thenAnswer(call -> memory.save(call.getArgument(0)));
    var indexing=new DocumentIndexingService(store, root.toString());
    indexing.apply(event("document.uploaded"), payload(1, "en", "original content"));
    var visible=indexing.current();
    doAnswer(call -> {
      java.util.function.Consumer<SearchIndexStore.StoredMutation> consume=call.getArgument(0);
      consume.accept(memory.load().get(0));
      throw new IllegalStateException("Recovery cursor failed midway");
    }).when(store).forEach(any());
    assertThatThrownBy(indexing::recover).isInstanceOf(IllegalStateException.class);
    assertThat(indexing.recovered()).isFalse();
    assertThat(indexing.current()).isSameAs(visible);
    assertThat(search(indexing, "original", ownerId).total()).isOne();
    doAnswer(call -> { memory.forEach(call.getArgument(0)); return null; }).when(store).forEach(any());
    indexing.recover();
    assertThat(indexing.recovered()).isTrue();
    assertThat(search(indexing, "original", ownerId).total()).isOne();
  }

  private DocumentIndexPayload withVersion(DocumentIndexPayload source, long version) {
    return new DocumentIndexPayload(
        1,
        source.documentId(),
        source.ownerId(),
        version,
        source.storageKey(),
        source.checksum(),
        source.language(),
        source.fileType(),
        source.uploadedAt());
  }

  private DomainEvent event(String type) {
    return new DomainEvent(UUID.randomUUID(), type, Instant.now(), "{}");
  }

  private static final class MemoryStore implements SearchIndexStore {
    private final Map<UUID, StoredMutation> rows = new LinkedHashMap<>();
    private long revision;

    @Override
    public OptionalLong save(Mutation mutation) {
      var previous = rows.get(mutation.documentId());
      if (previous != null && previous.mutation().version() >= mutation.version())
        return OptionalLong.empty();
      rows.put(mutation.documentId(), new StoredMutation(++revision, mutation));
      return OptionalLong.of(revision);
    }

    @Override
    public List<StoredMutation> load() {
      return new ArrayList<>(rows.values());
    }
  }
}

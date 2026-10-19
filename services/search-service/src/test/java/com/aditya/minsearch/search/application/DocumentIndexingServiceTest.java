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

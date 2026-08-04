package com.aditya.minsearch.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.document.domain.*;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentPersistenceAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentLifecycleServiceTest {
  @Test
  void rejectsUpdateByAnotherOwner() {
    DocumentPersistenceAdapter documents = mock(DocumentPersistenceAdapter.class);
    UUID owner = UUID.randomUUID();
    Document document = new Document(UUID.randomUUID(), owner, "title", "a.txt", "documents/a", "text/plain",
        FileType.TXT, 1, "checksum", "en", DocumentStatus.UPLOADED, 1, Instant.now(), Instant.now(), null);
    when(documents.findById(document.id())).thenReturn(java.util.Optional.of(document));
    DocumentLifecycleService service = new DocumentLifecycleService(documents, mock(DocumentOutboxService.class),
        Clock.fixed(Instant.now(), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.update(document.id(), UUID.randomUUID(), "new", "en"))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Document not found");
    verify(documents, never()).save(any());
  }

  @Test
  void updatesMetadataWithNextVersionAndOutboxEvent() {
    DocumentPersistenceAdapter documents = mock(DocumentPersistenceAdapter.class);
    DocumentOutboxService outbox = mock(DocumentOutboxService.class);
    UUID owner = UUID.randomUUID();
    Instant now = Instant.parse("2026-07-18T00:00:00Z");
    Document document = document(owner, DocumentStatus.UPLOADED, 3, null);
    when(documents.findById(document.id())).thenReturn(java.util.Optional.of(document));
    when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    DocumentLifecycleService service = new DocumentLifecycleService(documents, outbox,
        Clock.fixed(now, ZoneOffset.UTC));

    Document updated = service.update(document.id(), owner, "  New title  ", "fr");

    assertThat(updated.title()).isEqualTo("New title");
    assertThat(updated.language()).isEqualTo("fr");
    assertThat(updated.currentVersion()).isEqualTo(4);
    assertThat(updated.status()).isEqualTo(DocumentStatus.UPLOADED);
    verify(outbox).recordUpdated(updated, now);
  }

  @Test
  void marksDocumentDeletePendingAndRecordsCleanupEvent() {
    DocumentPersistenceAdapter documents = mock(DocumentPersistenceAdapter.class);
    DocumentOutboxService outbox = mock(DocumentOutboxService.class);
    UUID owner = UUID.randomUUID();
    Instant now = Instant.parse("2026-07-18T00:00:00Z");
    Document document = document(owner, DocumentStatus.UPLOADED, 2, null);
    when(documents.findById(document.id())).thenReturn(java.util.Optional.of(document));
    when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    DocumentLifecycleService service = new DocumentLifecycleService(documents, outbox,
        Clock.fixed(now, ZoneOffset.UTC));

    Document deleted = service.delete(document.id(), owner);

    assertThat(deleted.status()).isEqualTo(DocumentStatus.DELETE_PENDING);
    assertThat(deleted.deletedAt()).isEqualTo(now);
    verify(outbox).recordDeleted(deleted, now);
  }

  private static Document document(UUID owner, DocumentStatus status, long version, Instant deletedAt) {
    Instant created = Instant.parse("2026-07-17T00:00:00Z");
    return new Document(UUID.randomUUID(), owner, "title", "a.txt", "documents/a", "text/plain",
        FileType.TXT, 1, "checksum", "en", status, version, created, created, deletedAt);
  }
}

package com.aditya.minsearch.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.document.domain.Document;
import com.aditya.minsearch.document.domain.storage.DocumentStorage;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentPersistenceAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentUploadServiceTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void deletesStoredContentWhenMetadataPersistenceFails() throws Exception {
    DocumentPersistenceAdapter documents = mock(DocumentPersistenceAdapter.class);
    DocumentStorage storage = mock(DocumentStorage.class);
    DocumentOutboxService outbox = mock(DocumentOutboxService.class);
    when(documents.save(any())).thenThrow(new IllegalStateException("database unavailable"));
    DocumentUploadService service = service(documents, storage, outbox);
    MockMultipartFile file = file("notes.txt", "hello");

    assertThatThrownBy(() -> service.upload(UUID.randomUUID(), file, null, "en"))
        .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");

    verify(storage).delete(argThat(key -> key.startsWith("documents/") && key.endsWith("-notes.txt")));
    verifyNoInteractions(outbox);
  }

  @Test
  void concurrentUploadsUseDistinctDocumentAndStorageKeys() throws Exception {
    DocumentPersistenceAdapter documents = mock(DocumentPersistenceAdapter.class);
    DocumentStorage storage = mock(DocumentStorage.class);
    DocumentOutboxService outbox = mock(DocumentOutboxService.class);
    when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    DocumentUploadService service = service(documents, storage, outbox);
    UUID owner = UUID.randomUUID();
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Future<Document>> uploads = IntStream.range(0, 100)
          .mapToObj(index -> executor.submit(() -> service.upload(owner, file("doc.txt", "content-" + index), null, "en")))
          .toList();
      List<Document> documentsCreated = uploads.stream().map(this::get).toList();
      assertThat(documentsCreated).hasSize(100);
      assertThat(documentsCreated.stream().map(Document::id).distinct()).hasSize(100);
      assertThat(documentsCreated.stream().map(Document::storageKey).distinct()).hasSize(100);
      verify(outbox, times(100)).recordUploaded(any(Document.class), eq(CLOCK.instant()));
    } finally {
      executor.shutdownNow();
    }
  }

  private DocumentUploadService service(DocumentPersistenceAdapter documents, DocumentStorage storage,
      DocumentOutboxService outbox) {
    return new DocumentUploadService(documents, storage, CLOCK, outbox, 1_000_000);
  }

  private static MockMultipartFile file(String name, String content) {
    return new MockMultipartFile("file", name, "text/plain", content.getBytes());
  }

  private Document get(Future<Document> future) {
    try {
      return future.get();
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}

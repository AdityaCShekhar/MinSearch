package com.aditya.minsearch.document.application;

import com.aditya.minsearch.document.domain.Document;
import com.aditya.minsearch.document.domain.DocumentStatus;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentPersistenceAdapter;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentLifecycleService {
  private final DocumentPersistenceAdapter documents;
  private final DocumentOutboxService outbox;
  private final Clock clock;

  public DocumentLifecycleService(DocumentPersistenceAdapter documents, DocumentOutboxService outbox, Clock clock) {
    this.documents = documents; this.outbox = outbox; this.clock = clock;
  }

  @Transactional
  public Document update(UUID id, UUID ownerId, String title, String language) {
    Document current = owned(id, ownerId);
    Instant now = clock.instant();
    Document updated = new Document(current.id(), current.ownerId(), title == null || title.isBlank() ? current.title() : title.trim(),
        current.originalFilename(), current.storageKey(), current.mediaType(), current.fileType(), current.sizeBytes(),
        current.checksum(), language == null ? current.language() : language, DocumentStatus.UPLOADED,
        current.currentVersion() + 1, current.createdAt(), now, null);
    Document saved = documents.save(updated);
    outbox.recordUpdated(saved, now);
    return saved;
  }

  @Transactional
  public Document delete(UUID id, UUID ownerId) {
    Document current = owned(id, ownerId);
    Document deleted = new Document(current.id(), current.ownerId(), current.title(), current.originalFilename(),
        current.storageKey(), current.mediaType(), current.fileType(), current.sizeBytes(), current.checksum(),
        current.language(), DocumentStatus.DELETE_PENDING, current.currentVersion(), current.createdAt(), clock.instant(), clock.instant());
    Document saved = documents.save(deleted);
    outbox.recordDeleted(saved, saved.updatedAt());
    return saved;
  }

  private Document owned(UUID id, UUID ownerId) {
    Document document = documents.findById(id).filter(d -> d.deletedAt() == null)
        .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    if (!document.ownerId().equals(ownerId)) throw new IllegalArgumentException("Document not found");
    return document;
  }
}

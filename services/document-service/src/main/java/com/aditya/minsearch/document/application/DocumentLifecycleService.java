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
  private final com.aditya.minsearch.shared.domain.security.AuditTrail audit;

  public DocumentLifecycleService(
      DocumentPersistenceAdapter documents, DocumentOutboxService outbox, Clock clock) {
    this(documents, outbox, clock, (actor, action, resource, outcome) -> {});
  }

  @org.springframework.beans.factory.annotation.Autowired
  public DocumentLifecycleService(DocumentPersistenceAdapter documents, DocumentOutboxService outbox, Clock clock,
      com.aditya.minsearch.shared.domain.security.AuditTrail audit) {
    this.documents = documents;
    this.outbox = outbox;
    this.clock = clock;
    this.audit = audit;
  }

  @Transactional
  public Document update(UUID id, UUID ownerId, String title, String language) {
    return update(id,ownerId,title,language,false);
  }
  @Transactional
  public Document update(UUID id, UUID ownerId, String title, String language, boolean admin) {
    DocumentUploadValidator.metadata(title, language);
    Document current = owned(id, ownerId, admin);
    String nextTitle=title==null || title.isBlank() ? current.title() : title.trim();
    String nextLanguage=language==null ? current.language() : language;
    if (java.util.Objects.equals(nextTitle,current.title()) && java.util.Objects.equals(nextLanguage,current.language())) return current;
    Instant now = clock.instant();
    Document updated =
        new Document(
            current.id(),
            current.ownerId(),
            title == null || title.isBlank() ? current.title() : title.trim(),
            current.originalFilename(),
            current.storageKey(),
            current.mediaType(),
            current.fileType(),
            current.sizeBytes(),
            current.checksum(),
            language == null ? current.language() : language,
            DocumentStatus.UPLOADED,
            current.currentVersion() + 1,
            current.createdAt(),
            now,
            null);
    Document saved = documents.save(updated);
    outbox.recordUpdated(saved, now);
    return saved;
  }

  @Transactional
  public Document delete(UUID id, UUID ownerId) {
    return delete(id,ownerId,false);
  }
  @Transactional
  public Document delete(UUID id, UUID ownerId, boolean admin) {
    Document current = owned(id, ownerId, admin);
    Document deleted =
        new Document(
            current.id(),
            current.ownerId(),
            current.title(),
            current.originalFilename(),
            current.storageKey(),
            current.mediaType(),
            current.fileType(),
            current.sizeBytes(),
            current.checksum(),
            current.language(),
            DocumentStatus.DELETE_PENDING,
            current.currentVersion() + 1,
            current.createdAt(),
            clock.instant(),
            clock.instant());
    Document saved = documents.save(deleted);
    outbox.recordDeleted(saved, saved.updatedAt());
    audit.record(ownerId, "document.deleted", id, "SUCCESS");
    return saved;
  }

  @Transactional
  public Document reindex(UUID id, UUID actor, boolean admin) {
    if (!admin) throw new IllegalArgumentException("Administrator role required");
    var current=owned(id,actor,true);
    var updated=new Document(current.id(),current.ownerId(),current.title(),current.originalFilename(),current.storageKey(),
        current.mediaType(),current.fileType(),current.sizeBytes(),current.checksum(),current.language(),DocumentStatus.UPLOADED,
        current.currentVersion()+1,current.createdAt(),clock.instant(),null);
    var saved=documents.save(updated); outbox.recordUpdated(saved,saved.updatedAt()); audit.record(actor,"admin.document.reindex",id,"SUCCESS"); return saved;
  }

  private Document owned(UUID id, UUID ownerId, boolean admin) {
    Document document =
        documents
            .findByIdForUpdate(id)
            .filter(d -> d.deletedAt() == null)
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    if (!admin && !document.ownerId().equals(ownerId))
      throw new IllegalArgumentException("Document not found");
    return document;
  }
}

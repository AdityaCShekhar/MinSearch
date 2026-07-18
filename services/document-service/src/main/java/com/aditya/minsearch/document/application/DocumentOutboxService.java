package com.aditya.minsearch.document.application;

import com.aditya.minsearch.document.domain.Document;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentOutboxJpaEntity;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentOutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentOutboxService {
  private final DocumentOutboxRepository repository;
  public DocumentOutboxService(DocumentOutboxRepository repository) { this.repository = repository; }

  public void recordUploaded(Document document, Instant occurredAt) {
    record(document, "document.uploaded", occurredAt);
  }

  public void recordUpdated(Document document, Instant occurredAt) { record(document, "document.updated", occurredAt); }
  public void recordDeleted(Document document, Instant occurredAt) { record(document, "document.deleted", occurredAt); }

  private void record(Document document, String type, Instant occurredAt) {
    String payload = "{\"documentId\":\"" + document.id() + "\",\"version\":" + document.currentVersion()
        + ",\"storageKey\":\"" + document.storageKey() + "\",\"checksum\":\"" + document.checksum() + "\"}";
    repository.save(new DocumentOutboxJpaEntity(UUID.randomUUID(), document.id(), type, payload, occurredAt));
  }
}

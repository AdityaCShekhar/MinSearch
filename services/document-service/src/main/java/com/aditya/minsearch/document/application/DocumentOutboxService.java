package com.aditya.minsearch.document.application;

import com.aditya.minsearch.document.domain.Document;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentOutboxJpaEntity;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentOutboxRepository;
import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class DocumentOutboxService {
  private final DocumentOutboxRepository repository;
  private final ObjectMapper mapper;

  public DocumentOutboxService(DocumentOutboxRepository repository, ObjectMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  public void recordUploaded(Document document, Instant occurredAt) {
    record(document, "document.uploaded", occurredAt);
  }

  public void recordUpdated(Document document, Instant occurredAt) {
    record(document, "document.updated", occurredAt);
  }

  public void recordDeleted(Document document, Instant occurredAt) {
    record(document, "document.deleted", occurredAt);
  }

  private void record(Document document, String type, Instant occurredAt) {
    String payload =
        mapper.writeValueAsString(
            new DocumentIndexPayload(
                1,
                document.id(),
                document.ownerId(),
                document.currentVersion(),
                document.storageKey(),
                document.checksum(),
                document.language(),
                document.fileType().name(),
                document.createdAt(),document.title()));
    repository.save(
        new DocumentOutboxJpaEntity(UUID.randomUUID(), document.id(), type, payload, occurredAt));
  }
}

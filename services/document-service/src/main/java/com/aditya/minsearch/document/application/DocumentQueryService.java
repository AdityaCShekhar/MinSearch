package com.aditya.minsearch.document.application;

import com.aditya.minsearch.document.domain.Document;
import com.aditya.minsearch.document.infrastructure.persistence.DocumentPersistenceAdapter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentQueryService {
  private final DocumentPersistenceAdapter documents;

  public DocumentQueryService(DocumentPersistenceAdapter documents) {
    this.documents = documents;
  }

  public Document get(UUID id) {
    return documents
        .findById(id)
        .filter(document -> document.deletedAt() == null)
        .orElseThrow(() -> new IllegalArgumentException("Document not found"));
  }

  public List<Document> list(UUID ownerId) {
    return documents.findAllByOwnerId(ownerId);
  }

  public ProcessingStatus status(UUID id, UUID ownerId) {
    Document document =
        documents
            .findById(id)
            .filter(d -> d.ownerId().equals(ownerId))
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    return new ProcessingStatus(
        document.id(), document.currentVersion(), document.status(), document.indexingError());
  }

  public record ProcessingStatus(
      UUID documentId,
      long version,
      com.aditya.minsearch.document.domain.DocumentStatus status,
      String indexingError) {}
}

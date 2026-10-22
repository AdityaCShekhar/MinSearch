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

  private Document get(UUID id) {
    return documents
        .findById(id)
        .filter(document -> document.deletedAt() == null)
        .orElseThrow(() -> new IllegalArgumentException("Document not found"));
  }
  public Document get(UUID id, UUID actor, boolean admin) {
    var document=get(id);
    if (!admin && !document.ownerId().equals(actor)) throw new IllegalArgumentException("Document not found");
    return document;
  }
  public List<Document> list(UUID actor, boolean admin, int page, int size) { return documents.list(actor,admin,page,size); }

  public List<Document> list(UUID ownerId) {
    return documents.findAllByOwnerId(ownerId);
  }

  public ProcessingStatus status(UUID id, UUID ownerId) {
    return status(id,ownerId,false);
  }
  public ProcessingStatus status(UUID id, UUID ownerId, boolean admin) {
    Document document =
        documents
            .findById(id)
            .filter(d -> admin || d.ownerId().equals(ownerId))
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

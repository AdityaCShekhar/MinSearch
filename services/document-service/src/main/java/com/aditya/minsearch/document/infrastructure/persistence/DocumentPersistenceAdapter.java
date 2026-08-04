package com.aditya.minsearch.document.infrastructure.persistence;

import com.aditya.minsearch.document.domain.Document;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentPersistenceAdapter {
  private final DocumentRepository repository;
  public DocumentPersistenceAdapter(DocumentRepository repository) { this.repository = repository; }
  public Document save(Document document) { return repository.save(new DocumentJpaEntity(document)).toDomain(); }
  public java.util.Optional<Document> findById(UUID id) { return repository.findById(id).map(DocumentJpaEntity::toDomain); }
  public java.util.List<Document> findAllByOwnerId(UUID ownerId) {
    return repository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId).stream()
        .map(DocumentJpaEntity::toDomain).toList();
  }
}

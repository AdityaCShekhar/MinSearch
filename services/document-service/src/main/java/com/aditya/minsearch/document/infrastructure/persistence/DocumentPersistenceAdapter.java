package com.aditya.minsearch.document.infrastructure.persistence;

import com.aditya.minsearch.document.domain.Document;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentPersistenceAdapter {
  private final DocumentRepository repository;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public DocumentPersistenceAdapter(DocumentRepository repository) {
    this(repository, null);
  }
  @org.springframework.beans.factory.annotation.Autowired
  public DocumentPersistenceAdapter(DocumentRepository repository, org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.repository=repository; this.jdbc=jdbc;
  }

  public Document save(Document document) {
    var saved=repository.saveAndFlush(new DocumentJpaEntity(document)).toDomain();
    if (jdbc!=null) {
      jdbc.update("INSERT INTO document_versions(id,document_id,version,storage_key,checksum,size_bytes,title,language,created_at) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(document_id,version) DO NOTHING",
          UUID.randomUUID(), saved.id(), saved.currentVersion(), saved.storageKey(), saved.checksum(), saved.sizeBytes(), saved.title(), saved.language(), java.sql.Timestamp.from(saved.updatedAt()));
    }
    return saved;
  }

  public java.util.Optional<Document> findById(UUID id) {
    return repository.findById(id).map(DocumentJpaEntity::toDomain);
  }

  public java.util.Optional<Document> findByIdForUpdate(UUID id) {
    return repository.findByIdForUpdate(id).map(DocumentJpaEntity::toDomain);
  }

  public java.util.List<Document> findAllByOwnerId(UUID ownerId) {
    return repository.findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId).stream()
        .map(DocumentJpaEntity::toDomain)
        .toList();
  }

  public java.util.List<Document> list(UUID owner, boolean admin, int page, int size) {
    if (page<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid pagination");
    var request=org.springframework.data.domain.PageRequest.of(page,size,
        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"), org.springframework.data.domain.Sort.Order.asc("id")));
    return (admin ? repository.findAllByDeletedAtIsNull(request) : repository.findAllByOwnerIdAndDeletedAtIsNull(owner,request))
        .stream().map(DocumentJpaEntity::toDomain).toList();
  }
}

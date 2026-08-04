package com.aditya.minsearch.document.infrastructure.persistence;

import com.aditya.minsearch.document.domain.Document;
import com.aditya.minsearch.document.domain.DocumentStatus;
import com.aditya.minsearch.document.domain.FileType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
class DocumentJpaEntity {
  @Id UUID id;
  @Column(name = "owner_id", nullable = false) UUID ownerId;
  @Column(nullable = false, length = 500) String title;
  @Column(name = "original_filename", nullable = false) String originalFilename;
  @Column(name = "storage_key", nullable = false, unique = true) String storageKey;
  @Column(name = "media_type", nullable = false) String mediaType;
  @Enumerated(EnumType.STRING) @Column(name = "file_type", nullable = false) FileType fileType;
  @Column(name = "size_bytes", nullable = false) long sizeBytes;
  @Column(nullable = false, length = 64) String checksum;
  @Column(length = 16) String language;
  @Enumerated(EnumType.STRING) @Column(nullable = false) DocumentStatus status;
  @Column(name = "current_version", nullable = false) long currentVersion;
  @Column(name = "created_at", nullable = false) Instant createdAt;
  @Column(name = "updated_at", nullable = false) Instant updatedAt;
  @Column(name = "deleted_at") Instant deletedAt;

  protected DocumentJpaEntity() {}

  DocumentJpaEntity(Document d) {
    id=d.id(); ownerId=d.ownerId(); title=d.title(); originalFilename=d.originalFilename();
    storageKey=d.storageKey(); mediaType=d.mediaType(); fileType=d.fileType(); sizeBytes=d.sizeBytes();
    checksum=d.checksum(); language=d.language(); status=d.status(); currentVersion=d.currentVersion();
    createdAt=d.createdAt(); updatedAt=d.updatedAt(); deletedAt=d.deletedAt();
  }

  Document toDomain() { return new Document(id, ownerId, title, originalFilename, storageKey, mediaType,
      fileType, sizeBytes, checksum, language, status, currentVersion, createdAt, updatedAt, deletedAt); }
}

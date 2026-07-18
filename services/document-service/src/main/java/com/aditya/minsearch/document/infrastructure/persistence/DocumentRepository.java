package com.aditya.minsearch.document.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentRepository extends JpaRepository<DocumentJpaEntity, UUID> {
  List<DocumentJpaEntity> findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerId);
}

package com.aditya.minsearch.document.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DocumentRepository extends JpaRepository<DocumentJpaEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select document from DocumentJpaEntity document where document.id = :id")
  Optional<DocumentJpaEntity> findByIdForUpdate(@Param("id") UUID id);

  List<DocumentJpaEntity> findAllByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerId);
}

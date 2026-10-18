package com.aditya.minsearch.document.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface DocumentOutboxRepository extends JpaRepository<DocumentOutboxJpaEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<DocumentOutboxJpaEntity> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();
}

package com.aditya.minsearch.document.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentOutboxRepository extends JpaRepository<DocumentOutboxJpaEntity, UUID> {}

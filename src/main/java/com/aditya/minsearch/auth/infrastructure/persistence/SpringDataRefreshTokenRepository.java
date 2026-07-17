package com.aditya.minsearch.auth.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

	Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);
}

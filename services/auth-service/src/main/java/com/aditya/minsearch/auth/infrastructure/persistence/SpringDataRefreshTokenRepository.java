package com.aditya.minsearch.auth.infrastructure.persistence;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select token from RefreshTokenJpaEntity token where token.id = :id")
	Optional<RefreshTokenJpaEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

	Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

	List<RefreshTokenJpaEntity> findByFamilyId(UUID familyId);
}

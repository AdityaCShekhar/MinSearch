package com.aditya.minsearch.auth.infrastructure.persistence;

import com.aditya.minsearch.auth.domain.RefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenJpaEntity {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	RefreshTokenJpaEntity() {
	}

	RefreshTokenJpaEntity(RefreshToken refreshToken) {
		this.id = refreshToken.id();
		this.userId = refreshToken.userId();
		this.familyId = refreshToken.familyId();
		this.tokenHash = refreshToken.tokenHash();
		this.expiresAt = refreshToken.expiresAt();
		this.usedAt = refreshToken.usedAt();
		this.revokedAt = refreshToken.revokedAt();
		this.createdAt = refreshToken.createdAt();
	}

	RefreshToken toDomain() {
		return new RefreshToken(id, userId, familyId, tokenHash, expiresAt, usedAt, revokedAt, createdAt);
	}
}

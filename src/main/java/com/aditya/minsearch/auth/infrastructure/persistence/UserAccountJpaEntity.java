package com.aditya.minsearch.auth.infrastructure.persistence;

import com.aditya.minsearch.auth.domain.UserAccount;
import com.aditya.minsearch.auth.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserAccountJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	UserAccountJpaEntity() {
	}

	UserAccountJpaEntity(UserAccount userAccount) {
		this.id = userAccount.id();
		this.email = userAccount.email();
		this.passwordHash = userAccount.passwordHash();
		this.role = userAccount.role();
		this.createdAt = userAccount.createdAt();
		this.updatedAt = userAccount.updatedAt();
	}

	UserAccount toDomain() {
		return new UserAccount(id, email, passwordHash, role, createdAt, updatedAt);
	}
}

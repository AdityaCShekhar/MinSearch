package com.aditya.minsearch.auth.infrastructure.persistence;

import com.aditya.minsearch.auth.domain.RefreshToken;
import com.aditya.minsearch.auth.domain.RefreshTokenRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRefreshTokenRepositoryAdapter implements RefreshTokenRepository {

	private final SpringDataRefreshTokenRepository repository;

	public JpaRefreshTokenRepositoryAdapter(SpringDataRefreshTokenRepository repository) {
		this.repository = repository;
	}

	@Override
	public RefreshToken save(RefreshToken refreshToken) {
		return repository.save(new RefreshTokenJpaEntity(refreshToken)).toDomain();
	}

	@Override
	public Optional<RefreshToken> findById(UUID id) {
		return repository.findById(id).map(RefreshTokenJpaEntity::toDomain);
	}

	@Override
	public Optional<RefreshToken> findByTokenHash(String tokenHash) {
		return repository.findByTokenHash(tokenHash).map(RefreshTokenJpaEntity::toDomain);
	}

	@Override
	public List<RefreshToken> findByFamilyId(UUID familyId) {
		return repository.findByFamilyId(familyId).stream().map(RefreshTokenJpaEntity::toDomain).toList();
	}
}

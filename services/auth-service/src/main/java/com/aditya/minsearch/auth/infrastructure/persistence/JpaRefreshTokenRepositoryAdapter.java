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
	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	public JpaRefreshTokenRepositoryAdapter(SpringDataRefreshTokenRepository repository, org.springframework.jdbc.core.JdbcTemplate jdbc) {
		this.repository = repository;
		this.jdbc = jdbc;
	}

	@Override public void lockFamily(UUID familyId) {
		jdbc.execute(connection -> {
			var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)");
			statement.setLong(1, familyId.getMostSignificantBits() ^ familyId.getLeastSignificantBits());
			return statement;
		}, (org.springframework.jdbc.core.PreparedStatementCallback<Void>) statement -> { statement.execute(); return null; });
	}

	@Override
	public RefreshToken save(RefreshToken refreshToken) {
		return repository.save(new RefreshTokenJpaEntity(refreshToken)).toDomain();
	}

	@Override
	@org.springframework.transaction.annotation.Transactional
	public Optional<RefreshToken> findById(UUID id) {
		return repository.findByIdForUpdate(id).map(RefreshTokenJpaEntity::toDomain);
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

package com.aditya.minsearch.auth.domain;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface RefreshTokenRepository {
	RefreshToken save(RefreshToken refreshToken);

	Optional<RefreshToken> findById(UUID id);

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findByFamilyId(UUID familyId);
}

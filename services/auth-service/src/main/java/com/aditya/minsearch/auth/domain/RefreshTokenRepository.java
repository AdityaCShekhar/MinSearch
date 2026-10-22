package com.aditya.minsearch.auth.domain;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface RefreshTokenRepository {
  /** Serialize rotations and reuse revocation for one family in the surrounding transaction. */
  default void lockFamily(UUID familyId) {}
	RefreshToken save(RefreshToken refreshToken);

	Optional<RefreshToken> findById(UUID id);

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findByFamilyId(UUID familyId);
}

package com.aditya.minsearch.auth.domain;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {
	UserAccount save(UserAccount userAccount);

	Optional<UserAccount> findById(UUID id);

	Optional<UserAccount> findByNormalizedEmail(String normalizedEmail);
}

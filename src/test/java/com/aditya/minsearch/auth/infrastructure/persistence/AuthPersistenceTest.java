package com.aditya.minsearch.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.aditya.minsearch.auth.domain.RefreshToken;
import com.aditya.minsearch.auth.domain.RefreshTokenRepository;
import com.aditya.minsearch.auth.domain.UserAccount;
import com.aditya.minsearch.auth.domain.UserAccountRepository;
import com.aditya.minsearch.auth.domain.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthPersistenceTest {

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Test
	void savesAndLoadsUserAccountsAndRefreshTokens() {
		Instant now = Instant.parse("2026-07-17T10:00:00Z");
		UUID userId = UUID.fromString("c8c5dc1a-0de7-4d02-ad17-a41809e08724");
		UserAccount savedUser = userAccountRepository.save(new UserAccount(userId, "user@example.com", "hash", UserRole.USER, now, now));
		assertThat(userAccountRepository.findById(userId)).contains(savedUser);
		assertThat(userAccountRepository.findByNormalizedEmail("user@example.com")).contains(savedUser);

		UUID tokenId = UUID.fromString("7d0226c8-6b4e-481f-994b-132ab39ed65d");
		RefreshToken savedToken = refreshTokenRepository.save(new RefreshToken(tokenId, userId, UUID.randomUUID(), "token-hash", now.plusSeconds(3600), null, null, now));
		assertThat(refreshTokenRepository.findById(tokenId)).contains(savedToken);
		assertThat(refreshTokenRepository.findByTokenHash("token-hash")).contains(savedToken);
	}
}

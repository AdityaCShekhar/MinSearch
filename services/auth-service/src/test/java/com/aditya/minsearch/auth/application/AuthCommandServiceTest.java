package com.aditya.minsearch.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aditya.minsearch.auth.domain.InvalidCredentialsException;
import com.aditya.minsearch.auth.domain.RefreshToken;
import com.aditya.minsearch.auth.domain.RefreshTokenRepository;
import com.aditya.minsearch.auth.domain.UserAccount;
import com.aditya.minsearch.auth.domain.UserAccountRepository;
import com.aditya.minsearch.auth.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthCommandServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");

	private InMemoryUserAccountRepository userAccountRepository;
	private InMemoryRefreshTokenRepository refreshTokenRepository;
	private PasswordEncoder passwordEncoder;
	private FakeTokenService tokenService;
	private AuthCommandService service;

	@BeforeEach
	void setUp() {
		userAccountRepository = new InMemoryUserAccountRepository();
		refreshTokenRepository = new InMemoryRefreshTokenRepository();
		passwordEncoder = new BCryptPasswordEncoder();
		tokenService = new FakeTokenService();
		service =
				new AuthCommandService(
						userAccountRepository,
						refreshTokenRepository,
						passwordEncoder,
						tokenService,
						Clock.fixed(NOW, ZoneOffset.UTC));

		UserAccount account =
				new UserAccount(
						UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
						"user@example.com",
						passwordEncoder.encode("secret-password"),
						UserRole.USER,
						NOW,
						NOW);
		userAccountRepository.save(account);
	}

	@Test
	void loginIssuesSignedTokensAndStoresHashedRefreshToken() {
		AuthTokens tokens = service.login("User@Example.com", "secret-password");

		assertThat(tokens.accessToken()).startsWith("access:");
		assertThat(tokens.refreshToken()).startsWith("rt.");
		assertThat(refreshTokenRepository.storedTokens()).hasSize(1);
		assertThat(refreshTokenRepository.storedTokens().get(0).tokenHash())
				.isNotEqualTo(tokens.refreshToken());
		assertThat(passwordEncoder.matches(tokens.refreshToken(), refreshTokenRepository.storedTokens().get(0).tokenHash()))
				.isTrue();
	}

	@Test
	void refreshRotatesTheTokenAndMarksThePreviousTokenUsed() {
		AuthTokens initial = service.login("user@example.com", "secret-password");
		RefreshToken first = refreshTokenRepository.storedTokens().get(0);

		AuthTokens rotated = service.refresh(initial.refreshToken());

		assertThat(rotated.refreshToken()).isNotEqualTo(initial.refreshToken());
		assertThat(refreshTokenRepository.storedTokens()).hasSize(2);
		assertThat(refreshTokenRepository.findById(first.id()).orElseThrow().usedAt()).isNotNull();
	}

	@Test
	void logoutRevokesRefreshToken() {
		AuthTokens initial = service.login("user@example.com", "secret-password");
		RefreshToken first = refreshTokenRepository.storedTokens().get(0);

		service.logout(initial.refreshToken());

		assertThat(refreshTokenRepository.findById(first.id()).orElseThrow().revokedAt()).isNotNull();
	}

	@Test
	void reuseOfARefreshTokenRevokesTheWholeFamily() {
		AuthTokens initial = service.login("user@example.com", "secret-password");
		service.refresh(initial.refreshToken());
		RefreshToken first = refreshTokenRepository.storedTokens().get(0);

		assertThatThrownBy(() -> service.refresh(initial.refreshToken()))
				.isInstanceOf(InvalidCredentialsException.class);
		assertThat(refreshTokenRepository.storedTokens()).allMatch(token -> token.revokedAt() != null || token.usedAt() != null);
		assertThat(refreshTokenRepository.findById(first.id()).orElseThrow().revokedAt()).isNotNull();
	}

	private static final class InMemoryUserAccountRepository implements UserAccountRepository {
		private final Map<UUID, UserAccount> byId = new HashMap<>();
		private final Map<String, UserAccount> byEmail = new HashMap<>();

		@Override
		public UserAccount save(UserAccount userAccount) {
			byId.put(userAccount.id(), userAccount);
			byEmail.put(userAccount.email(), userAccount);
			return userAccount;
		}

		@Override
		public Optional<UserAccount> findById(UUID id) {
			return Optional.ofNullable(byId.get(id));
		}

		@Override
		public Optional<UserAccount> findByNormalizedEmail(String normalizedEmail) {
			return Optional.ofNullable(byEmail.get(normalizedEmail));
		}
	}

	private static final class InMemoryRefreshTokenRepository implements RefreshTokenRepository {
		private final Map<UUID, RefreshToken> byId = new HashMap<>();

		@Override
		public RefreshToken save(RefreshToken refreshToken) {
			byId.put(refreshToken.id(), refreshToken);
			return refreshToken;
		}

		@Override
		public Optional<RefreshToken> findById(UUID id) {
			return Optional.ofNullable(byId.get(id));
		}

		@Override
		public Optional<RefreshToken> findByTokenHash(String tokenHash) {
			return byId.values().stream().filter(token -> token.tokenHash().equals(tokenHash)).findFirst();
		}

		@Override
		public List<RefreshToken> findByFamilyId(UUID familyId) {
			return byId.values().stream().filter(token -> token.familyId().equals(familyId)).toList();
		}

		List<RefreshToken> storedTokens() {
			return byId.values().stream().sorted((a, b) -> a.createdAt().compareTo(b.createdAt())).toList();
		}
	}

	private static final class FakeTokenService implements TokenService {
		private int counter;

		@Override
		public String createAccessToken(UserAccount userAccount, UUID tokenId, Instant issuedAt, Instant expiresAt) {
			return "access:" + (++counter) + ":" + tokenId;
		}

		@Override
		public String createRefreshToken(
				UserAccount userAccount, UUID tokenId, UUID familyId, Instant issuedAt, Instant expiresAt) {
			return "rt." + tokenId.toString().replace("-", "") + "." + familyId.toString().replace("-", "");
		}

		@Override
		public AccessTokenClaims decodeAccessToken(String token) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RefreshTokenClaims decodeRefreshToken(String token) {
			String[] parts = token.split("\\.");
			UUID tokenId = parseCompactUuid(parts[1]);
			UUID familyId = parseCompactUuid(parts[2]);
			return new RefreshTokenClaims(
					UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
					tokenId,
					familyId,
					NOW,
					NOW.plusSeconds(AuthCommandService.REFRESH_TOKEN_SECONDS),
					"minsearch");
		}

		private UUID parseCompactUuid(String value) {
			if (value.length() != 32) {
				throw new IllegalArgumentException("invalid token");
			}
			return UUID.fromString(
					value.substring(0, 8)
							+ "-"
							+ value.substring(8, 12)
							+ "-"
							+ value.substring(12, 16)
							+ "-"
							+ value.substring(16, 20)
							+ "-"
							+ value.substring(20));
		}
	}
}

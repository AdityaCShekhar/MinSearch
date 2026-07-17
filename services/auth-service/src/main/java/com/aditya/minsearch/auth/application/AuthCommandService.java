package com.aditya.minsearch.auth.application;

import com.aditya.minsearch.auth.domain.RefreshToken;
import com.aditya.minsearch.auth.domain.RefreshTokenRepository;
import com.aditya.minsearch.auth.domain.InvalidCredentialsException;
import com.aditya.minsearch.auth.domain.UserAccount;
import com.aditya.minsearch.auth.domain.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthCommandService {

	public static final String ACCESS_ISSUER = "minsearch";
	public static final long ACCESS_TOKEN_SECONDS = 15 * 60;
	public static final long REFRESH_TOKEN_SECONDS = 30L * 24 * 60 * 60;

	private final UserAccountRepository userAccountRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final Clock clock;

	public AuthCommandService(
			UserAccountRepository userAccountRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			TokenService tokenService,
			Clock clock) {
		this.userAccountRepository = userAccountRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.clock = clock;
	}

	@Transactional
	public AuthTokens login(String email, String password) {
		UserAccount userAccount = findAuthenticatedAccount(email, password);
		return issueTokens(userAccount, UUID.randomUUID());
	}

	@Transactional
	public AuthTokens refresh(String refreshToken) {
		TokenService.RefreshTokenClaims claims = tokenService.decodeRefreshToken(refreshToken);
		RefreshToken storedRefreshToken =
				refreshTokenRepository
						.findById(claims.tokenId())
						.orElseThrow(InvalidCredentialsException::new);
		if (storedRefreshToken.usedAt() != null || storedRefreshToken.revokedAt() != null) {
			revokeFamily(storedRefreshToken.familyId());
			throw new InvalidCredentialsException();
		}
		validateRefreshToken(storedRefreshToken, refreshToken);
		UserAccount userAccount =
				userAccountRepository
						.findById(storedRefreshToken.userId())
						.orElseThrow(InvalidCredentialsException::new);
		RefreshToken revoked = markUsed(storedRefreshToken);
		refreshTokenRepository.save(revoked);
		return issueTokens(userAccount, storedRefreshToken.familyId());
	}

	@Transactional
	public void logout(String refreshToken) {
		TokenService.RefreshTokenClaims claims = tokenService.decodeRefreshToken(refreshToken);
		RefreshToken storedRefreshToken =
				refreshTokenRepository
						.findById(claims.tokenId())
						.orElseThrow(InvalidCredentialsException::new);
		validateRefreshToken(storedRefreshToken, refreshToken);
		refreshTokenRepository.save(revoke(storedRefreshToken));
	}

	private UserAccount findAuthenticatedAccount(String email, String password) {
		UserAccount userAccount =
				userAccountRepository
						.findByNormalizedEmail(RegistrationService.normalizeEmail(email))
						.orElseThrow(InvalidCredentialsException::new);
		if (!passwordEncoder.matches(password, userAccount.passwordHash())) {
			throw new InvalidCredentialsException();
		}
		return userAccount;
	}

	private AuthTokens issueTokens(UserAccount userAccount, UUID familyId) {
		Instant now = clock.instant();
		UUID accessTokenId = UUID.randomUUID();
		UUID refreshTokenId = UUID.randomUUID();
		String accessToken =
				tokenService.createAccessToken(
						userAccount, accessTokenId, now, now.plusSeconds(ACCESS_TOKEN_SECONDS));
		String refreshToken =
				tokenService.createRefreshToken(
						userAccount, refreshTokenId, familyId, now, now.plusSeconds(REFRESH_TOKEN_SECONDS));
		refreshTokenRepository.save(
				new RefreshToken(
						refreshTokenId,
						userAccount.id(),
						familyId,
						passwordEncoder.encode(refreshToken),
						now.plusSeconds(REFRESH_TOKEN_SECONDS),
						null,
						null,
						now));
		return new AuthTokens(accessToken, refreshToken);
	}

	private void validateRefreshToken(RefreshToken storedRefreshToken, String presentedToken) {
		if (storedRefreshToken.revokedAt() != null || storedRefreshToken.usedAt() != null) {
			throw new InvalidCredentialsException();
		}
		if (storedRefreshToken.expiresAt().isBefore(clock.instant())) {
			throw new InvalidCredentialsException();
		}
		if (!passwordEncoder.matches(presentedToken, storedRefreshToken.tokenHash())) {
			throw new InvalidCredentialsException();
		}
	}

	private RefreshToken revoke(RefreshToken refreshToken) {
		Instant now = clock.instant();
		return new RefreshToken(
				refreshToken.id(),
				refreshToken.userId(),
				refreshToken.familyId(),
				refreshToken.tokenHash(),
				refreshToken.expiresAt(),
				refreshToken.usedAt(),
				now,
				refreshToken.createdAt());
	}

	private RefreshToken markUsed(RefreshToken refreshToken) {
		Instant now = clock.instant();
		return new RefreshToken(
				refreshToken.id(),
				refreshToken.userId(),
				refreshToken.familyId(),
				refreshToken.tokenHash(),
				refreshToken.expiresAt(),
				now,
				refreshToken.revokedAt(),
				refreshToken.createdAt());
	}

	private void revokeFamily(UUID familyId) {
		for (RefreshToken refreshToken : refreshTokenRepository.findByFamilyId(familyId)) {
			if (refreshToken.revokedAt() == null) {
				refreshTokenRepository.save(revoke(refreshToken));
			}
		}
	}
}

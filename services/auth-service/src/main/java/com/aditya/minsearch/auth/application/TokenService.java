package com.aditya.minsearch.auth.application;

import com.aditya.minsearch.auth.domain.UserAccount;
import java.time.Instant;
import java.util.UUID;

public interface TokenService {

	String createAccessToken(UserAccount userAccount, UUID tokenId, Instant issuedAt, Instant expiresAt);

	String createRefreshToken(
			UserAccount userAccount, UUID tokenId, UUID familyId, Instant issuedAt, Instant expiresAt);

	AccessTokenClaims decodeAccessToken(String token);

	RefreshTokenClaims decodeRefreshToken(String token);

	record AccessTokenClaims(UUID subject, String role, UUID tokenId, Instant issuedAt, Instant expiresAt, String issuer) {}

	record RefreshTokenClaims(UUID subject, UUID tokenId, UUID familyId, Instant issuedAt, Instant expiresAt, String issuer) {}
}

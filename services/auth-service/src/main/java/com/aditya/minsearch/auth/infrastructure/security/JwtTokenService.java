package com.aditya.minsearch.auth.infrastructure.security;

import com.aditya.minsearch.auth.application.TokenService;
import com.aditya.minsearch.auth.domain.UserAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService implements TokenService {

	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;
	private final String issuer;

	public JwtTokenService(@Value("${minsearch.security.jwt.secret}") String secret) {
		this.issuer = "minsearch";
		SecretKey secretKey = new SecretKeySpec(normalizeSecret(secret), "HmacSHA256");
		this.jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build();
		this.jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder() {
		return jwtDecoder;
	}

	@Override
	public String createAccessToken(UserAccount userAccount, UUID tokenId, Instant issuedAt, Instant expiresAt) {
		return jwtEncoder
				.encode(
						JwtEncoderParameters.from(
								JwtClaimsSet.builder()
										.issuer(issuer)
										.subject(userAccount.id().toString())
										.issuedAt(issuedAt)
										.expiresAt(expiresAt)
										.id(tokenId.toString())
										.claim("role", userAccount.role().name())
										.build()))
				.getTokenValue();
	}

	@Override
	public String createRefreshToken(
			UserAccount userAccount, UUID tokenId, UUID familyId, Instant issuedAt, Instant expiresAt) {
		return "rt."
				+ compactUuid(tokenId)
				+ "."
				+ compactUuid(familyId);
	}

	@Override
	public AccessTokenClaims decodeAccessToken(String token) {
		Jwt jwt = jwtDecoder.decode(token);
		return new AccessTokenClaims(
				UUID.fromString(jwt.getSubject()),
				jwt.getClaimAsString("role"),
				UUID.fromString(jwt.getId()),
				jwt.getIssuedAt(),
				jwt.getExpiresAt(),
				jwt.getIssuer().toString());
	}

	@Override
	public RefreshTokenClaims decodeRefreshToken(String token) {
		if (!token.startsWith("rt.")) {
			throw new IllegalArgumentException("Invalid refresh token");
		}
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			throw new IllegalArgumentException("Invalid refresh token");
		}
		return new RefreshTokenClaims(
				UUID.randomUUID(),
				decompactUuid(parts[1]),
				decompactUuid(parts[2]),
				Instant.now(),
				Instant.now(),
				issuer);
	}

	private static byte[] normalizeSecret(String secret) {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
		}
		return bytes;
	}

	private static String compactUuid(UUID uuid) {
		byte[] bytes = new byte[16];
		long most = uuid.getMostSignificantBits();
		long least = uuid.getLeastSignificantBits();
		for (int i = 0; i < 8; i++) {
			bytes[i] = (byte) (most >>> (8 * (7 - i)));
			bytes[i + 8] = (byte) (least >>> (8 * (7 - i)));
		}
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static UUID decompactUuid(String value) {
		byte[] bytes = Base64.getUrlDecoder().decode(value);
		if (bytes.length != 16) {
			throw new IllegalArgumentException("Invalid refresh token");
		}
		long most = 0L;
		long least = 0L;
		for (int i = 0; i < 8; i++) {
			most = (most << 8) | (bytes[i] & 0xffL);
			least = (least << 8) | (bytes[i + 8] & 0xffL);
		}
		return new UUID(most, least);
	}
}

package com.aditya.minsearch.shared.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

/** Explicit current/previous key rotation; JWT-controlled type or key loading is never enabled. */
public final class JwtKeys {
  private JwtKeys() {}
  public static byte[] bytes(String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes");
    return secret.getBytes(StandardCharsets.UTF_8);
  }
  public static JwtDecoder decoder(String current, String previous) {
    JwtDecoder primary = single(current);
    JwtDecoder retiring = previous == null || previous.isBlank() ? null : single(previous);
    return token -> {
      try { return primary.decode(token); }
      catch (JwtException failed) {
        if (retiring == null) throw failed;
        return retiring.decode(token);
      }
    };
  }
  private static JwtDecoder single(String secret) {
    var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes(secret), "HmacSHA256"))
        .macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> identity = jwt -> {
      try {
        UUID.fromString(jwt.getSubject());
        UUID.fromString(jwt.getId());
        if (!Set.of("USER", "ADMIN").contains(jwt.getClaimAsString("role"))
            || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
            || jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(60))
            || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) throw new IllegalArgumentException();
        return OAuth2TokenValidatorResult.success();
      } catch (RuntimeException invalid) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required token claims are invalid", null));
      }
    };
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("minsearch"), identity));
    return decoder;
  }
}

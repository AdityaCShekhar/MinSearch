package com.aditya.minsearch.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class JwtKeysTest {
  static final String CURRENT = "cccccccccccccccccccccccccccccccccccccccccccccccc", PREVIOUS = "p".repeat(48);
  static String token(String secret, String issuer, String subject, String role, Instant issued, Instant expires) {
    return NimbusJwtEncoder.withSecretKey(new SecretKeySpec(JwtKeys.bytes(secret), "HmacSHA256"))
        .algorithm(MacAlgorithm.HS256).build().encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
            .issuer(issuer).subject(subject).id(UUID.randomUUID().toString()).claim("role", role)
            .issuedAt(issued).expiresAt(expires).build())).getTokenValue();
  }
  @Test void rotatesKeysWithRequiredIdentityClaims() {
    var now = Instant.now();
    String id = UUID.randomUUID().toString();
    String old = token(PREVIOUS, "minsearch", id, "USER", now, now.plusSeconds(900));
    assertThat(JwtKeys.decoder(CURRENT, PREVIOUS).decode(old).getSubject()).isEqualTo(id);
    assertThatThrownBy(() -> JwtKeys.decoder(CURRENT, "").decode(old)).isInstanceOf(JwtException.class);
    for (String invalid : new String[]{
        token(CURRENT, "other", id, "USER", now, now.plusSeconds(900)),
        token(CURRENT, "minsearch", "not-a-uuid", "USER", now, now.plusSeconds(900)),
        token(CURRENT, "minsearch", id, "ROOT", now, now.plusSeconds(900)),
        token(CURRENT, "minsearch", id, "USER", now.minusSeconds(900), now.minusSeconds(300)),
        token(CURRENT, "minsearch", id, "USER", now.plusSeconds(300), now.plusSeconds(900))}) {
      assertThatThrownBy(() -> JwtKeys.decoder(CURRENT, PREVIOUS).decode(invalid)).isInstanceOf(JwtException.class);
    }
  }
  @Test void productionRejectsDefaultsAndAcceptsExternalSecrets() {
    var env = new org.springframework.mock.env.MockEnvironment()
        .withProperty("minsearch.deployment.mode", "production")
        .withProperty("minsearch.security.jwt.secret", "local-only-change-this-minsearch-secret-key")
        .withProperty("spring.datasource.password", "minsearch_dev_password");
    assertThatThrownBy(() -> new DeploymentSecretGuard(env).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
    env.withProperty("minsearch.security.jwt.secret", CURRENT).withProperty("spring.datasource.password", "d".repeat(40));
    new DeploymentSecretGuard(env).afterPropertiesSet();
    env.withProperty("minsearch.cache.redis.enabled", "true");
    assertThatThrownBy(() -> new DeploymentSecretGuard(env).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
    env.withProperty("spring.data.redis.password", "r".repeat(40));
    new DeploymentSecretGuard(env).afterPropertiesSet();
    assertThatThrownBy(() -> new SecurityPolicy(java.util.List.of("*"), 1200, 65536)).isInstanceOf(IllegalArgumentException.class);
  }
}

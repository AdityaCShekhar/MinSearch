package com.aditya.minsearch.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AuthPersistenceTest {
  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", postgres::getJdbcUrl);
    properties.add("spring.datasource.username", postgres::getUsername);
    properties.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private UserAccountRepository userAccountRepository;

  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private com.aditya.minsearch.auth.application.AuthCommandService commands;
  @Autowired private com.aditya.minsearch.auth.application.RegistrationService registration;
  @Autowired private com.aditya.minsearch.auth.application.TokenService tokens;

  @Test void reuseRevocationCommitsDespiteRejectedRequest() {
    String email = UUID.randomUUID() + "@example.com";
    registration.register(email, "valid-password");
    var initial = commands.login(email, "valid-password");
    var rotated = commands.refresh(initial.refreshToken());
    assertThatThrownBy(() -> commands.refresh(initial.refreshToken()))
        .isInstanceOf(com.aditya.minsearch.auth.domain.InvalidCredentialsException.class);
    assertThatThrownBy(() -> commands.refresh(rotated.refreshToken()))
        .isInstanceOf(com.aditya.minsearch.auth.domain.InvalidCredentialsException.class);
    assertThat(refreshTokenRepository.findByFamilyId(tokens.decodeRefreshToken(initial.refreshToken()).familyId()))
        .allSatisfy(token -> assertThat(token.revokedAt()).isNotNull());
  }

  @Test void concurrentRefreshAllowsOneRotationThenRevokesReusedFamily() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    registration.register(email, "valid-password");
    var initial = commands.login(email, "valid-password");
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.Callable<Boolean> refresh = () -> {
      start.await();
      try { commands.refresh(initial.refreshToken()); return true; }
      catch (com.aditya.minsearch.auth.domain.InvalidCredentialsException rejected) { return false; }
    };
    try {
      var first = executor.submit(refresh); var second = executor.submit(refresh); start.countDown();
      assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(refreshTokenRepository.findByFamilyId(tokens.decodeRefreshToken(initial.refreshToken()).familyId()))
          .hasSize(2).allSatisfy(token -> assertThat(token.revokedAt()).isNotNull());
    } finally { executor.shutdownNow(); }
  }

  @Test
  void savesAndLoadsUserAccountsAndRefreshTokens() {
    Instant now = Instant.parse("2026-07-17T10:00:00Z");
    UUID userId = UUID.fromString("c8c5dc1a-0de7-4d02-ad17-a41809e08724");
    UserAccount savedUser =
        userAccountRepository.save(
            new UserAccount(userId, "user@example.com", "hash", UserRole.USER, now, now));
    assertThat(userAccountRepository.findById(userId)).contains(savedUser);
    assertThat(userAccountRepository.findByNormalizedEmail("user@example.com")).contains(savedUser);

    UUID tokenId = UUID.fromString("7d0226c8-6b4e-481f-994b-132ab39ed65d");
    RefreshToken savedToken =
        refreshTokenRepository.save(
            new RefreshToken(
                tokenId,
                userId,
                UUID.randomUUID(),
                "token-hash",
                now.plusSeconds(3600),
                null,
                null,
                now));
    assertThat(refreshTokenRepository.findById(tokenId)).contains(savedToken);
    assertThat(refreshTokenRepository.findByTokenHash("token-hash")).contains(savedToken);
  }
}

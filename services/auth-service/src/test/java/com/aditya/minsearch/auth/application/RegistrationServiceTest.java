package com.aditya.minsearch.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aditya.minsearch.auth.domain.UserAccount;
import com.aditya.minsearch.auth.domain.UserAccountRepository;
import com.aditya.minsearch.auth.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Optional;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RegistrationServiceTest {

	@Test
	void normalizesEmailAndHashesPassword() {
		InMemoryUserAccountRepository repository = new InMemoryUserAccountRepository();

		RegistrationService service =
				new RegistrationService(
						repository,
						new BCryptPasswordEncoder(),
						Clock.fixed(Instant.parse("2026-07-17T10:00:00Z"), ZoneOffset.UTC));

		UserAccount account = service.register("  User@Example.com  ", "secret-password");

		assertThat(account.email()).isEqualTo("user@example.com");
		assertThat(account.role()).isEqualTo(UserRole.USER);
		assertThat(account.passwordHash()).isNotEqualTo("secret-password");
		assertThat(new BCryptPasswordEncoder().matches("secret-password", account.passwordHash()))
				.isTrue();
	}

	@Test
	void rejectsDuplicateNormalizedEmail() {
		InMemoryUserAccountRepository repository = new InMemoryUserAccountRepository();
		repository.save(
				new UserAccount(
						java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
						"user@example.com",
						"hash",
						UserRole.USER,
						Instant.parse("2026-07-17T10:00:00Z"),
						Instant.parse("2026-07-17T10:00:00Z")));

		RegistrationService service =
				new RegistrationService(
						repository,
						new BCryptPasswordEncoder(),
						Clock.systemUTC());

		assertThatThrownBy(() -> service.register("User@example.com", "secret-password"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static final class InMemoryUserAccountRepository implements UserAccountRepository {

		private final Map<String, UserAccount> accounts = new HashMap<>();

		@Override
		public Optional<UserAccount> findById(java.util.UUID id) {
			return accounts.values().stream().filter(account -> account.id().equals(id)).findFirst();
		}

		@Override
		public Optional<UserAccount> findByNormalizedEmail(String normalizedEmail) {
			return Optional.ofNullable(accounts.get(normalizedEmail));
		}

		@Override
		public UserAccount save(UserAccount userAccount) {
			accounts.put(userAccount.email(), userAccount);
			return userAccount;
		}
	}
}

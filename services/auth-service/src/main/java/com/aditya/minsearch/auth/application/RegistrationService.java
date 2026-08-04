package com.aditya.minsearch.auth.application;

import com.aditya.minsearch.auth.domain.UserAccount;
import com.aditya.minsearch.auth.domain.UserAccountRepository;
import com.aditya.minsearch.auth.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public RegistrationService(
			UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, Clock clock) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Transactional
	public UserAccount register(String email, String password) {
		String normalizedEmail = normalizeEmail(email);
		if (userAccountRepository.findByNormalizedEmail(normalizedEmail).isPresent()) {
			throw new IllegalArgumentException("Email is already registered");
		}

		Instant now = clock.instant();
		UserAccount account =
				new UserAccount(
						UUID.randomUUID(),
						normalizedEmail,
						passwordEncoder.encode(password),
						UserRole.USER,
						now,
						now);
		return userAccountRepository.save(account);
	}

	static String normalizeEmail(String email) {
		String value = Objects.requireNonNull(email, "email must not be null").trim();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("Email must not be blank");
		}
		return value.toLowerCase(Locale.ROOT);
	}
}

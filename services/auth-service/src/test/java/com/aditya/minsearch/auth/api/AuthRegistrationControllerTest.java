package com.aditya.minsearch.auth.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aditya.minsearch.auth.application.RegistrationService;
import com.aditya.minsearch.auth.domain.UserAccount;
import com.aditya.minsearch.auth.domain.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthRegistrationControllerTest {

	private MockMvc mockMvc;
	private RegistrationService registrationService;

	@BeforeEach
	void setUp() {
		registrationService =
				new RegistrationService(null, null, java.time.Clock.systemUTC()) {
					@Override
					public UserAccount register(String email, String password) {
						return new UserAccount(
								UUID.fromString("c8c5dc1a-0de7-4d02-ad17-a41809e08724"),
								"user@example.com",
								"hash",
								UserRole.USER,
								Instant.parse("2026-07-17T10:00:00Z"),
								Instant.parse("2026-07-17T10:00:00Z"));
					}
				};
		mockMvc =
				MockMvcBuilders.standaloneSetup(
								new AuthRegistrationController(registrationService, new TestAuthenticationRateLimiter()))
						.build();
	}

	@Test
	void registersAUser() throws Exception {
		mockMvc
				.perform(
						post("/api/v1/auth/register")
								.contentType(APPLICATION_JSON)
								.content("{\"email\":\"user@example.com\",\"password\":\"secret-password\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/auth/users/c8c5dc1a-0de7-4d02-ad17-a41809e08724"))
				.andExpect(jsonPath("$.id").value("c8c5dc1a-0de7-4d02-ad17-a41809e08724"))
				.andExpect(jsonPath("$.email").value("user@example.com"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	private static final class TestAuthenticationRateLimiter
			extends com.aditya.minsearch.auth.infrastructure.security.AuthenticationRateLimiter {

		TestAuthenticationRateLimiter() {
			super(java.time.Clock.systemUTC());
		}

		@Override
		public void enforce(String key) {}
	}
}

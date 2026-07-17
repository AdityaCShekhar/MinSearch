package com.aditya.minsearch.auth.api;

import com.aditya.minsearch.auth.application.RegistrationRequest;
import com.aditya.minsearch.auth.application.RegistrationService;
import com.aditya.minsearch.auth.domain.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import com.aditya.minsearch.auth.infrastructure.security.AuthenticationRateLimiter;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthRegistrationController {

	private final RegistrationService registrationService;
	private final AuthenticationRateLimiter rateLimiter;

	public AuthRegistrationController(
			RegistrationService registrationService, AuthenticationRateLimiter rateLimiter) {
		this.registrationService = registrationService;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping("/register")
	@Operation(summary = "Register a new user")
	@ApiResponses(
			{
				@ApiResponse(
						responseCode = "201",
						description = "User registered",
						content = @Content(schema = @Schema(implementation = RegistrationResponse.class))),
				@ApiResponse(responseCode = "400", description = "Invalid registration request"),
				@ApiResponse(responseCode = "429", description = "Too many authentication attempts")
			})
	public ResponseEntity<RegistrationResponse> register(
			@Valid @RequestBody RegistrationRequest request, HttpServletRequest httpServletRequest) {
		rateLimiter.enforce("register:" + httpServletRequest.getRemoteAddr());
		UserAccount account = registrationService.register(request.email(), request.password());
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/auth/users/" + account.id()))
				.body(new RegistrationResponse(account.id().toString(), account.email(), account.role().name()));
	}

	public record RegistrationResponse(
			@Schema(example = "c8c5dc1a-0de7-4d02-ad17-a41809e08724") String id,
			@Schema(example = "user@example.com") String email,
			@Schema(example = "USER") String role) {}
}

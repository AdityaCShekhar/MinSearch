package com.aditya.minsearch.auth.api;

import com.aditya.minsearch.auth.application.AuthCommandService;
import com.aditya.minsearch.auth.application.AuthTokens;
import com.aditya.minsearch.auth.infrastructure.security.AuthenticationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
@Tag(name = "Authentication")
public class AuthSessionController {

	private final AuthCommandService authCommandService;
	private final AuthenticationRateLimiter rateLimiter;

	public AuthSessionController(
			AuthCommandService authCommandService, AuthenticationRateLimiter rateLimiter) {
		this.authCommandService = authCommandService;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping("/login")
	@Operation(summary = "Authenticate a user and issue access and refresh tokens")
	@ApiResponses(
			{
				@ApiResponse(
						responseCode = "200",
						description = "Authentication successful",
						content = @Content(schema = @Schema(implementation = AuthTokens.class))),
				@ApiResponse(responseCode = "401", description = "Invalid credentials"),
				@ApiResponse(responseCode = "429", description = "Too many authentication attempts")
			})
	public ResponseEntity<AuthTokens> login(
			@RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
		rateLimiter.enforce("login:" + httpServletRequest.getRemoteAddr());
		return ResponseEntity.ok(authCommandService.login(request.email(), request.password()));
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rotate a refresh token and issue a new token pair")
	@ApiResponses(
			{
				@ApiResponse(
						responseCode = "200",
						description = "Refresh successful",
						content = @Content(schema = @Schema(implementation = AuthTokens.class))),
				@ApiResponse(responseCode = "401", description = "Invalid credentials"),
				@ApiResponse(responseCode = "429", description = "Too many authentication attempts")
			})
	public ResponseEntity<AuthTokens> refresh(
			@RequestBody RefreshRequest request, HttpServletRequest httpServletRequest) {
		rateLimiter.enforce("refresh:" + httpServletRequest.getRemoteAddr());
		return ResponseEntity.ok(authCommandService.refresh(request.refreshToken()));
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoke a refresh token")
	@ApiResponses(
			{
				@ApiResponse(responseCode = "204", description = "Refresh token revoked"),
				@ApiResponse(responseCode = "401", description = "Invalid credentials"),
				@ApiResponse(responseCode = "429", description = "Too many authentication attempts")
			})
	@SecurityRequirement(name = "bearerAuth")
	public ResponseEntity<Void> logout(
			@RequestBody RefreshRequest request, HttpServletRequest httpServletRequest) {
		rateLimiter.enforce("logout:" + httpServletRequest.getRemoteAddr());
		authCommandService.logout(request.refreshToken());
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	public record LoginRequest(
			@Schema(example = "user@example.com") @NotBlank String email,
			@Schema(example = "secret-password") @NotBlank String password) {}

	public record RefreshRequest(
			@Schema(example = "rt.YWJj...") @NotBlank String refreshToken) {}
}

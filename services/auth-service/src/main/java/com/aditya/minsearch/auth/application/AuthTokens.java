package com.aditya.minsearch.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Issued authentication tokens")
public record AuthTokens(
		@Schema(description = "Short-lived access token", example = "eyJhbGciOiJIUzI1NiJ9...")
		String accessToken,
		@Schema(description = "Rotating refresh token", example = "rt.YWJj...") String refreshToken) {}

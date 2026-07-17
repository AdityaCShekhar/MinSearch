package com.aditya.minsearch.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(
	UUID id,
	UUID userId,
	UUID familyId,
	String tokenHash,
	Instant expiresAt,
	Instant usedAt,
	Instant revokedAt,
	Instant createdAt
) {}

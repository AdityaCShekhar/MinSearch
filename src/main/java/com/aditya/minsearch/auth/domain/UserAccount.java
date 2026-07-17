package com.aditya.minsearch.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
	UUID id,
	String email,
	String passwordHash,
	UserRole role,
	Instant createdAt,
	Instant updatedAt
) {}

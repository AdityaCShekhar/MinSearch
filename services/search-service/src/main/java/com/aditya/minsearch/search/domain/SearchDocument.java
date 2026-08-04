package com.aditya.minsearch.search.domain;

import java.time.Instant;
import java.util.UUID;

public record SearchDocument(UUID id, UUID ownerId, String language, String type, Instant uploadedAt) {}

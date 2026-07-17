package com.aditya.minsearch.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent(UUID eventId, String eventType, Instant occurredAt, String payload) {}

package com.aditya.minsearch.document.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_outbox")
public class DocumentOutboxJpaEntity {
  @Id @Column(name = "event_id") UUID eventId;
  @Column(name = "aggregate_id", nullable = false) UUID aggregateId;
  @Column(name = "event_type", nullable = false) String eventType;
  @Column(nullable = false, columnDefinition = "TEXT") String payload;
  @Column(name = "occurred_at", nullable = false) Instant occurredAt;
  @Column(name = "published_at") Instant publishedAt;
  @Column(nullable = false) int attempts;
  @Column(name = "last_error") String lastError;

  protected DocumentOutboxJpaEntity() {}

  public DocumentOutboxJpaEntity(UUID eventId, UUID aggregateId, String eventType, String payload, Instant occurredAt) {
    this.eventId = eventId; this.aggregateId = aggregateId; this.eventType = eventType;
    this.payload = payload; this.occurredAt = occurredAt; this.attempts = 0;
  }
  public UUID eventId() { return eventId; }
  public UUID aggregateId() { return aggregateId; }
  public String eventType() { return eventType; }
  public String payload() { return payload; }
  public Instant occurredAt() { return occurredAt; }
  public void markPublished(Instant time) { publishedAt = time; }
  public void markFailed(String error) { attempts++; lastError = error; }
}

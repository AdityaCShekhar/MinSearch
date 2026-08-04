package com.aditya.minsearch.document.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEventJpaEntity {
  @Id @Column(name = "event_id") UUID eventId;
  @Column(name = "processed_at", nullable = false) Instant processedAt;
  protected ProcessedEventJpaEntity() {}
  public ProcessedEventJpaEntity(UUID eventId, Instant processedAt) { this.eventId = eventId; this.processedAt = processedAt; }
}

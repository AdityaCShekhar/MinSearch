package com.aditya.minsearch.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.document.infrastructure.persistence.*;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import com.aditya.minsearch.shared.domain.event.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentOutboxProcessorTest {
  private final DocumentOutboxRepository outbox = mock(DocumentOutboxRepository.class);
  private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
  private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void publishesAndMarksPendingEventProcessed() {
    UUID eventId = UUID.randomUUID();
    DocumentOutboxJpaEntity event = new DocumentOutboxJpaEntity(eventId, UUID.randomUUID(), "document.uploaded", "{}", clock.instant());
    when(outbox.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(event));
    when(processed.existsById(eventId)).thenReturn(false);

    int count = new DocumentOutboxProcessor(outbox, processed, publisher, clock).publishPending();

    assertThat(count).isOne();
    verify(publisher).publish(any(DomainEvent.class));
    verify(processed).save(any(ProcessedEventJpaEntity.class));
    verify(outbox, atLeastOnce()).save(event);
  }

  @Test
  void skipsAlreadyProcessedEvent() {
    UUID eventId = UUID.randomUUID();
    DocumentOutboxJpaEntity event = new DocumentOutboxJpaEntity(eventId, UUID.randomUUID(), "document.updated", "{}", clock.instant());
    when(outbox.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(event));
    when(processed.existsById(eventId)).thenReturn(true);

    assertThat(new DocumentOutboxProcessor(outbox, processed, publisher, clock).publishPending()).isZero();
    verifyNoInteractions(publisher);
  }
}

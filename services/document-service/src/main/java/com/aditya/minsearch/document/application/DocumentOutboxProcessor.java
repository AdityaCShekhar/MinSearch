package com.aditya.minsearch.document.application;

import com.aditya.minsearch.document.infrastructure.persistence.*;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import com.aditya.minsearch.shared.domain.event.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentOutboxProcessor {
  private final DocumentOutboxRepository outbox;
  private final ProcessedEventRepository processed;
  private final DomainEventPublisher publisher;
  private final Clock clock;

  public DocumentOutboxProcessor(DocumentOutboxRepository outbox, ProcessedEventRepository processed,
      DomainEventPublisher publisher, Clock clock) {
    this.outbox = outbox; this.processed = processed; this.publisher = publisher; this.clock = clock;
  }

  @Transactional
  public int publishPending() {
    int published = 0;
    for (DocumentOutboxJpaEntity entry : outbox.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc()) {
      if (processed.existsById(entry.eventId())) { entry.markPublished(clock.instant()); outbox.save(entry); continue; }
      try {
        publisher.publish(new DomainEvent(entry.eventId(), entry.eventType(), entry.occurredAt(), entry.payload()));
        processed.save(new ProcessedEventJpaEntity(entry.eventId(), clock.instant()));
        entry.markPublished(clock.instant()); outbox.save(entry); published++;
      } catch (RuntimeException error) {
        entry.markFailed(error.getClass().getSimpleName() + ": " + error.getMessage()); outbox.save(entry);
      }
    }
    return published;
  }
}

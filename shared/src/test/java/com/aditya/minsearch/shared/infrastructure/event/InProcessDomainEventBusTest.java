package com.aditya.minsearch.shared.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.aditya.minsearch.shared.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InProcessDomainEventBusTest {

  @Test
  void preservesPublishedEventOrder() {
    InProcessDomainEventBus bus = new InProcessDomainEventBus();
    DomainEvent first =
        new DomainEvent(
            UUID.randomUUID(), "document.uploaded", Instant.parse("2026-07-17T10:00:00Z"), "{}");
    DomainEvent second =
        new DomainEvent(
            UUID.randomUUID(), "document.deleted", Instant.parse("2026-07-17T10:01:00Z"), "{}");

    bus.publish(first);
    bus.publish(second);

    assertThat(bus.publishedEvents()).containsExactly(first, second);
  }
}

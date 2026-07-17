package com.aditya.minsearch.shared.infrastructure.event;

import com.aditya.minsearch.shared.domain.event.DomainEvent;
import com.aditya.minsearch.shared.domain.event.DomainEventPublisher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InProcessDomainEventBus implements DomainEventPublisher {

  private final List<DomainEvent> publishedEvents = new ArrayList<>();

  @Override
  public synchronized void publish(DomainEvent event) {
    publishedEvents.add(event);
  }

  public synchronized List<DomainEvent> publishedEvents() {
    return Collections.unmodifiableList(new ArrayList<>(publishedEvents));
  }
}

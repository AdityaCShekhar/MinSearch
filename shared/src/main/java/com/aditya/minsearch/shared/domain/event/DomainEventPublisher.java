package com.aditya.minsearch.shared.domain.event;

public interface DomainEventPublisher {

  void publish(DomainEvent event);
}

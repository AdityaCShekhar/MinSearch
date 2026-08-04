package com.aditya.minsearch.document.infrastructure;

import com.aditya.minsearch.shared.domain.event.DomainEventPublisher;
import com.aditya.minsearch.shared.infrastructure.event.InProcessDomainEventBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentEventConfiguration {
  @Bean DomainEventPublisher domainEventPublisher() { return new InProcessDomainEventBus(); }
}

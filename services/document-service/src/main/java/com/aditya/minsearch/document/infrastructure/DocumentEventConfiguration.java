package com.aditya.minsearch.document.infrastructure;

import com.aditya.minsearch.shared.domain.event.DomainEventPublisher;
import com.aditya.minsearch.shared.infrastructure.event.InProcessDomainEventBus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentEventConfiguration {
  @Bean
  @ConditionalOnProperty(
      name = "minsearch.messaging.enabled",
      havingValue = "false",
      matchIfMissing = true)
  DomainEventPublisher domainEventPublisher() {
    return new InProcessDomainEventBus();
  }
}

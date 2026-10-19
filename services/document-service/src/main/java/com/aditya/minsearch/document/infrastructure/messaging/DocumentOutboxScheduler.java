package com.aditya.minsearch.document.infrastructure.messaging;

import com.aditya.minsearch.document.application.DocumentOutboxProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "minsearch.messaging.enabled", havingValue = "true")
public class DocumentOutboxScheduler {
  private final DocumentOutboxProcessor processor;

  public DocumentOutboxScheduler(DocumentOutboxProcessor processor) {
    this.processor = processor;
  }

  @Scheduled(fixedDelayString = "${minsearch.messaging.outbox-delay-ms:1000}")
  public void publishPending() {
    processor.publishPending();
  }
}

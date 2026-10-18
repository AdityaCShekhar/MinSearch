package com.aditya.minsearch.document.infrastructure.messaging;

import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import com.aditya.minsearch.shared.domain.event.DomainEventPublisher;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "minsearch.messaging.enabled", havingValue = "true")
public class KafkaDocumentEventPublisher implements DomainEventPublisher {
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper mapper;

  public KafkaDocumentEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
    this.kafka = kafka;
    this.mapper = mapper;
  }

  @Override
  public void publish(DomainEvent event) {
    DocumentIndexPayload payload = mapper.readValue(event.payload(), DocumentIndexPayload.class);
    try {
      // An outbox row is marked published only after the broker acknowledges the send.
      kafka
          .send(
              event.eventType(), payload.documentId().toString(), mapper.writeValueAsString(event))
          .get(15, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while publishing document event", interrupted);
    } catch (Exception failure) {
      throw new IllegalStateException("Unable to publish document event", failure);
    }
  }
}

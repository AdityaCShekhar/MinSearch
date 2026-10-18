package com.aditya.minsearch.document.infrastructure.messaging;

import com.aditya.minsearch.document.application.DocumentIndexOutcomeService;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import com.aditya.minsearch.shared.domain.event.IndexOutcomePayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "minsearch.messaging.enabled", havingValue = "true")
public class KafkaIndexOutcomeListener {
  private final ObjectMapper mapper;
  private final DocumentIndexOutcomeService outcomes;

  public KafkaIndexOutcomeListener(ObjectMapper mapper, DocumentIndexOutcomeService outcomes) {
    this.mapper = mapper;
    this.outcomes = outcomes;
  }

  @KafkaListener(
      topics = {"index.completed", "index.failed"},
      groupId = "${minsearch.messaging.outcome-group-id:minsearch-documents}")
  public void consume(ConsumerRecord<String, String> record) {
    var event = mapper.readValue(record.value(), DomainEvent.class);
    var outcome = mapper.readValue(event.payload(), IndexOutcomePayload.class);
    String expectedTopic =
        outcome.outcome() == IndexOutcomePayload.Outcome.INDEX_FAILED
            ? "index.failed"
            : "index.completed";
    if (!expectedTopic.equals(record.topic())
        || !record.topic().equals(event.eventType())
        || !outcome.documentId().toString().equals(record.key())) {
      throw new IllegalArgumentException("Index outcome does not match its Kafka topic/key");
    }
    outcomes.accept(event.eventId(), outcome);
  }
}

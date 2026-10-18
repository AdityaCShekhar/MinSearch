package com.aditya.minsearch.search.infrastructure;

import com.aditya.minsearch.search.application.DocumentIndexingService;
import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import java.io.IOException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "minsearch.messaging.enabled", havingValue = "true")
public class KafkaDocumentEventListener {
  private final ObjectMapper mapper;
  private final DocumentIndexingService indexing;

  public KafkaDocumentEventListener(ObjectMapper mapper, DocumentIndexingService indexing) {
    this.mapper = mapper;
    this.indexing = indexing;
  }

  @KafkaListener(
      topics = {"document.uploaded", "document.updated", "document.deleted"},
      groupId = "${minsearch.messaging.group-id:minsearch-search}")
  public void consume(ConsumerRecord<String, String> record) throws IOException {
    DomainEvent event = mapper.readValue(record.value(), DomainEvent.class);
    DocumentIndexPayload payload = mapper.readValue(event.payload(), DocumentIndexPayload.class);
    if (!record.topic().equals(event.eventType())
        || !payload.documentId().toString().equals(record.key())) {
      throw new IllegalArgumentException("Document event does not match its Kafka topic/key");
    }
    indexing.apply(event, payload);
  }
}

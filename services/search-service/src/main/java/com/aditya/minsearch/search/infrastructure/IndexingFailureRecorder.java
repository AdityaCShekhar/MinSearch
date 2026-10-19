package com.aditya.minsearch.search.infrastructure;

import com.aditya.minsearch.index.application.TextExtractionException;
import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import java.nio.file.NoSuchFileException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class IndexingFailureRecorder {
  private static final Logger LOG = LoggerFactory.getLogger(IndexingFailureRecorder.class);
  private final ObjectMapper mapper;
  private final IndexOutcomeOutbox outbox;

  public IndexingFailureRecorder(ObjectMapper mapper, IndexOutcomeOutbox outbox) {
    this.mapper = mapper;
    this.outbox = outbox;
  }

  public void record(ConsumerRecord<?, ?> record, Exception failure) {
    DomainEvent source;
    DocumentIndexPayload document;
    try {
      source = mapper.readValue((String) record.value(), DomainEvent.class);
      document = mapper.readValue(source.payload(), DocumentIndexPayload.class);
      if (!source.eventType().equals(record.topic())
          || !document.documentId().toString().equals(record.key())) {
        throw new IllegalArgumentException("Mismatched event identity");
      }
    } catch (Exception malformed) {
      LOG.warn(
          "Dead-lettered record has no trustworthy document identity: {}:{}:{}",
          record.topic(),
          record.partition(),
          record.offset());
      return;
    }
    outbox.recordFailure(source, document, describe(failure));
  }

  private String describe(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof NoSuchFileException)
        return "Stored document content could not be found.";
      if ("Document checksum mismatch".equals(cause.getMessage()))
        return "Document content failed checksum validation.";
      if (cause instanceof TextExtractionException)
        return "Document content could not be extracted: " + cause.getMessage();
      if (cause instanceof IllegalArgumentException)
        return "Document metadata or storage key is invalid.";
    }
    return "Indexing failed after retry attempts.";
  }
}

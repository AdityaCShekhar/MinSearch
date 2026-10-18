package com.aditya.minsearch.search.infrastructure;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IndexingFailureRecorderTest {
  @Test
  void recordsSafeFailureDescriptionsForValidatedDocumentIdentities() {
    var mapper = JsonMapper.builder().build();
    var outbox = mock(IndexOutcomeOutbox.class);
    var payload =
        new DocumentIndexPayload(
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            "sample.txt",
            "checksum",
            "en",
            "TXT",
            Instant.now());
    var event =
        new DomainEvent(
            UUID.randomUUID(),
            "document.uploaded",
            Instant.now(),
            mapper.writeValueAsString(payload));
    var record =
        new ConsumerRecord<String, String>(
            "document.uploaded",
            0,
            1,
            payload.documentId().toString(),
            mapper.writeValueAsString(event));
    new IndexingFailureRecorder(mapper, outbox)
        .record(
            record,
            new IllegalStateException("wrapper", new NoSuchFileException("/private/storage/path")));
    verify(outbox)
        .recordFailure(eq(event), eq(payload), eq("Stored document content could not be found."));
  }

  @Test
  void malformedRecordsCannotChangeDocumentStatus() {
    var outbox = mock(IndexOutcomeOutbox.class);
    new IndexingFailureRecorder(JsonMapper.builder().build(), outbox)
        .record(
            new ConsumerRecord<String, String>(
                "document.uploaded", 0, 1, "invalid-key", "not-json"),
            new IllegalArgumentException("invalid"));
    verifyNoInteractions(outbox);
  }
}

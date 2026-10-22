package com.aditya.minsearch.search.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import com.aditya.minsearch.shared.domain.event.IndexOutcomePayload;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings({"unchecked", "rawtypes"})
class DeadLetterReplayTest {
  Consumer<String, String> consumer = mock(Consumer.class);
  Producer<String, String> producer = mock(Producer.class);
  ByteArrayOutputStream receipt = new ByteArrayOutputStream();
  TopicPartition source = new TopicPartition("document.uploaded.DLT", 2);
  UUID id = UUID.randomUUID();
  JsonMapper mapper = JsonMapper.builder().build();

  @BeforeEach
  void range() {
    when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(source, 0L));
    when(consumer.endOffsets(anyCollection())).thenReturn(Map.of(source, 10L));
  }

  @Test
  void previewsWithoutPublishingOrChangingConsumerOffsets() throws Exception {
    poll(4, id.toString(), body("document.uploaded"));
    replay(false);
    assertThat(receipt.toString()).contains("\"execute\":false", "\"sourceOffset\":4");
    verify(consumer).assign(List.of(source));
    verify(consumer).seek(source, 4);
    verifyNoInteractions(producer);
    verify(consumer, never()).commitSync();
  }

  @Test
  void republishesTheExactBodyKeyAndPartitionAfterBrokerAcknowledgement() throws Exception {
    String body = body("document.uploaded");
    poll(4, id.toString(), body);
    when(producer.send(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new RecordMetadata(new TopicPartition("document.uploaded", 2), 20, 0, 0, 0, 0)));
    replay(true);
    ArgumentCaptor<ProducerRecord> sent = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(producer).send(sent.capture());
    assertThat(sent.getValue().topic()).isEqualTo("document.uploaded");
    assertThat(sent.getValue().partition()).isEqualTo(2);
    assertThat(sent.getValue().key()).isEqualTo(id.toString());
    assertThat(sent.getValue().value()).isEqualTo(body);
    assertThat(sent.getValue().headers()).isEmpty();
    assertThat(receipt.toString()).contains("\"targetOffset\":20");
  }

  @Test
  void missingOrExpiredOffsetsCannotReplayTheNextAvailableRecord() {
    when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(source, 5L));
    assertThatThrownBy(() -> replay(true)).hasMessageContaining("retained partition range");
    when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(source, 0L));
    poll(5, id.toString(), body("document.uploaded"));
    assertThatThrownBy(() -> replay(true)).hasMessageContaining("different record");
    verifyNoInteractions(producer);
  }

  @Test
  void rejectsMalformedPayloadsAndMismatchedKeysAndTopics() {
    for (String body : List.of("not-json", body("document.updated"))) {
      poll(4, id.toString(), body);
      assertThatThrownBy(() -> replay(true)).isInstanceOf(RuntimeException.class);
    }
    poll(4, UUID.randomUUID().toString(), body("document.uploaded"));
    assertThatThrownBy(() -> replay(true)).hasMessageContaining("Kafka key");
    verifyNoInteractions(producer);
  }

  @Test
  void failedBrokerSendDoesNotPrintASuccessReceipt() {
    poll(4, id.toString(), body("document.uploaded"));
    when(producer.send(any()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
    assertThatThrownBy(() -> replay(true)).hasCauseInstanceOf(IllegalStateException.class);
    assertThat(receipt.toString()).isEmpty();
  }

  @Test
  void supportsOutcomeReplayAndRejectsMismatchedOutcomeType() throws Exception {
    source = new TopicPartition("index.completed.DLT", 2);
    range();
    var payload = new IndexOutcomePayload(1, id, 1, 2, IndexOutcomePayload.Outcome.INDEXED, null);
    String body =
        mapper.writeValueAsString(
            new DomainEvent(
                UUID.randomUUID(),
                "index.completed",
                Instant.now(),
                mapper.writeValueAsString(payload)));
    poll(4, id.toString(), body);
    replay(false);
    assertThat(receipt.toString()).contains("index.completed");
    payload =
        new IndexOutcomePayload(1, id, 1, 0, IndexOutcomePayload.Outcome.INDEX_FAILED, "Failure");
    body =
        mapper.writeValueAsString(
            new DomainEvent(
                UUID.randomUUID(),
                "index.completed",
                Instant.now(),
                mapper.writeValueAsString(payload)));
    poll(4, id.toString(), body);
    assertThatThrownBy(() -> replay(true)).hasMessageContaining("Outcome does not match");
    verifyNoInteractions(producer);
  }

  @Test
  void requiresExplicitCoordinatesAndAnAllowedDeadLetterTopic() {
    assertThatThrownBy(() -> DeadLetterReplay.Options.parse(new String[] {}))
        .isInstanceOf(IllegalArgumentException.class);
    for (String topic : List.of("document.uploaded", "unrelated.DLT")) {
      assertThatThrownBy(
              () ->
                  DeadLetterReplay.Options.parse(
                      new String[] {
                        "--bootstrap-server",
                        "localhost:9092",
                        "--topic",
                        topic,
                        "--partition",
                        "0",
                        "--offset",
                        "0"
                      }))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(
            DeadLetterReplay.Options.parse(
                    new String[] {
                      "--bootstrap-server",
                      "localhost:9092",
                      "--topic",
                      "document.uploaded.DLT",
                      "--partition",
                      "0",
                      "--offset",
                      "0"
                    })
                .execute())
        .isFalse();
  }

  private void replay(boolean execute) throws Exception {
    DeadLetterReplay.replay(
        consumer,
        producer,
        new DeadLetterReplay.Options("localhost:9092", source.topic(), 2, 4, execute, null),
        new PrintStream(receipt));
  }

  private String body(String type) {
    var payload =
        new DocumentIndexPayload(
            1, id, UUID.randomUUID(), 1, "sample.txt", "checksum", "en", "TXT", Instant.EPOCH);
    return mapper.writeValueAsString(
        new DomainEvent(
            UUID.randomUUID(), type, Instant.now(), mapper.writeValueAsString(payload)));
  }

  private void poll(long offset, String key, String body) {
    when(consumer.poll(any(java.time.Duration.class)))
        .thenReturn(
            new ConsumerRecords<>(
                Map.of(
                    source,
                    List.of(
                        new ConsumerRecord<>(
                            source.topic(), source.partition(), offset, key, body))),
                Map.of()));
  }
}

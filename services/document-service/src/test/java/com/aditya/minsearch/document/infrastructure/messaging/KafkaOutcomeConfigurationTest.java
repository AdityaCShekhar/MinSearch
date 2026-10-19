package com.aditya.minsearch.document.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

class KafkaOutcomeConfigurationTest {
  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void terminalRecoveryUsesTheProvisionedOutcomeDeadLetterTopic() {
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    ProducerFactory<String, String> producer = mock(ProducerFactory.class);
    when(kafka.getProducerFactory()).thenReturn(producer);
    when(producer.getConfigurationProperties()).thenReturn(Map.of("delivery.timeout.ms", 1000));
    when(kafka.send(any(ProducerRecord.class)))
        .thenAnswer(
            call -> {
              ProducerRecord<String, String> sent = call.getArgument(0);
              return CompletableFuture.completedFuture(
                  new SendResult<>(
                      sent,
                      new RecordMetadata(
                          new TopicPartition(sent.topic(), sent.partition()), 0, 0, 0, 0, 0)));
            });
    var handler = new KafkaOutcomeConfiguration().outcomeErrorHandler(kafka);
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    var record = new ConsumerRecord<String, String>("index.completed", 1, 1, "document-id", "body");
    Consumer<String, String> consumer = mock(Consumer.class);
    when(consumer.partitionsFor(anyString(), any()))
        .thenReturn(
            List.of(new PartitionInfo("index.completed.DLT", 1, null, new Node[0], new Node[0])));
    assertThat(
            handler.handleOne(
                new IllegalArgumentException("terminal"),
                record,
                consumer,
                mock(MessageListenerContainer.class)))
        .isTrue();
    ArgumentCaptor<ProducerRecord> sent = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka).send(sent.capture());
    assertThat(sent.getValue().topic()).isEqualTo("index.completed.DLT");
    assertThat(sent.getValue().partition()).isEqualTo(1);
  }
}

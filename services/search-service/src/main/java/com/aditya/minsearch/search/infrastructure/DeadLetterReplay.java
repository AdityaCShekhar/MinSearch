package com.aditya.minsearch.search.infrastructure;

import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import com.aditya.minsearch.shared.domain.event.IndexOutcomePayload;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import tools.jackson.databind.json.JsonMapper;

/** Replays one explicitly selected record without joining or changing any consumer group. */
public final class DeadLetterReplay {
  private static final Set<String> TOPICS =
      Set.of(
          "document.uploaded",
          "document.updated",
          "document.deleted",
          "index.completed",
          "index.failed");

  private DeadLetterReplay() {}

  public static void main(String[] args) throws Exception {
    Options options = Options.parse(args);
    Properties config = new Properties();
    if (options.config() != null) {
      try (var input = Files.newInputStream(Path.of(options.config()))) {
        config.load(input);
      }
    }
    config.put("bootstrap.servers", options.bootstrap());
    config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("enable.auto.commit", "false");
    config.put("auto.offset.reset", "none");
    config.put("allow.auto.create.topics", "false");
    config.remove("group.id");
    Properties producerConfig = new Properties();
    producerConfig.putAll(config);
    producerConfig.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    producerConfig.put(
        "value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    producerConfig.put("acks", "all");
    producerConfig.put("enable.idempotence", "true");
    producerConfig.put("request.timeout.ms", "10000");
    producerConfig.put("delivery.timeout.ms", "15000");
    producerConfig.put("max.block.ms", "10000");
    try (var consumer = new KafkaConsumer<String, String>(config);
        var producer =
            options.execute() ? new KafkaProducer<String, String>(producerConfig) : null) {
      replay(consumer, producer, options, System.out);
    }
  }

  static void replay(
      Consumer<String, String> consumer,
      Producer<String, String> producer,
      Options options,
      PrintStream output)
      throws Exception {
    TopicPartition source = new TopicPartition(options.topic(), options.partition());
    consumer.assign(List.of(source));
    long beginning = consumer.beginningOffsets(List.of(source)).get(source);
    long end = consumer.endOffsets(List.of(source)).get(source);
    if (options.offset() < beginning || options.offset() >= end)
      throw new IllegalArgumentException("Selected offset is outside the retained partition range");
    consumer.seek(source, options.offset());
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (System.nanoTime() < deadline) {
      for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
        if (!record.topic().equals(source.topic())
            || record.partition() != source.partition()
            || record.offset() != options.offset())
          throw new IllegalStateException(
              "Selected offset is missing; refusing to replay a different record");
        var mapper = JsonMapper.builder().build();
        DomainEvent event = mapper.readValue(record.value(), DomainEvent.class);
        String target = options.topic().substring(0, options.topic().length() - 4);
        if (event.eventId() == null
            || event.occurredAt() == null
            || !target.equals(event.eventType()))
          throw new IllegalArgumentException(
              "Dead-letter event identity does not match the source topic");
        String id;
        if (target.startsWith("document.")) {
          id =
              mapper.readValue(event.payload(), DocumentIndexPayload.class).documentId().toString();
        } else {
          var outcome = mapper.readValue(event.payload(), IndexOutcomePayload.class);
          String expected =
              outcome.outcome() == IndexOutcomePayload.Outcome.INDEX_FAILED
                  ? "index.failed"
                  : "index.completed";
          if (!expected.equals(target))
            throw new IllegalArgumentException("Outcome does not match topic");
          id = outcome.documentId().toString();
        }
        if (!id.equals(record.key()))
          throw new IllegalArgumentException("Document ID does not match Kafka key");
        Map<String, Object> receipt =
            new HashMap<>(
                Map.of(
                    "sourceTopic",
                    source.topic(),
                    "partition",
                    source.partition(),
                    "sourceOffset",
                    record.offset(),
                    "targetTopic",
                    target,
                    "eventId",
                    event.eventId().toString(),
                    "execute",
                    options.execute()));
        if (options.execute()) {
          // Keep the original event ID, payload and partition. Do not copy stale exception headers.
          var sent =
              producer
                  .send(
                      new ProducerRecord<>(
                          target, source.partition(), record.key(), record.value()))
                  .get(20, TimeUnit.SECONDS);
          receipt.put("targetOffset", sent.offset());
        }
        output.println(mapper.writeValueAsString(receipt));
        return;
      }
    }
    throw new IllegalStateException("Timed out reading the selected dead-letter record");
  }

  record Options(
      String bootstrap, String topic, int partition, long offset, boolean execute, String config) {
    static Options parse(String[] args) {
      Map<String, String> values = new HashMap<>();
      boolean execute = false;
      for (int i = 0; i < args.length; i++) {
        if (args[i].equals("--execute")) {
          execute = true;
          continue;
        }
        if (!Set.of("--bootstrap-server", "--topic", "--partition", "--offset", "--config")
                .contains(args[i])
            || i + 1 == args.length
            || values.put(args[i], args[++i]) != null)
          throw new IllegalArgumentException(
              "Use --bootstrap-server HOST --topic TOPIC.DLT --partition N --offset N [--config"
                  + " FILE] [--execute]");
      }
      String topic = values.getOrDefault("--topic", "");
      if (!topic.endsWith(".DLT") || !TOPICS.contains(topic.substring(0, topic.length() - 4)))
        throw new IllegalArgumentException("Select a provisioned MiniSearch .DLT topic");
      int partition = Integer.parseInt(values.getOrDefault("--partition", "-1"));
      long offset = Long.parseLong(values.getOrDefault("--offset", "-1"));
      String bootstrap = values.get("--bootstrap-server");
      if (partition < 0 || offset < 0 || bootstrap == null || bootstrap.isBlank())
        throw new IllegalArgumentException(
            "Bootstrap server, nonnegative partition and offset are required");
      return new Options(bootstrap, topic, partition, offset, execute, values.get("--config"));
    }
  }
}

package com.aditya.minsearch.search.infrastructure;

import com.aditya.minsearch.search.application.DocumentIndexingService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "minsearch.messaging.enabled", havingValue = "true")
public class IndexOutcomeRelay {
  private static final Logger LOG = LoggerFactory.getLogger(IndexOutcomeRelay.class);
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final DocumentIndexingService indexing;

  public IndexOutcomeRelay(
      JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, DocumentIndexingService indexing) {
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.indexing = indexing;
  }

  @Scheduled(fixedDelayString = "${minsearch.messaging.outcome-delay-ms:1000}")
  @Transactional
  public void publishPending() {
    var events =
        jdbc.query(
            """
            SELECT event_id, document_id, event_type, body, attempts FROM index_outbox
            WHERE published_at IS NULL AND next_attempt_at <= now() AND required_generation <= ?
            ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED
            """,
            (row, number) ->
                new Pending(
                    row.getObject("event_id", UUID.class),
                    row.getObject("document_id", UUID.class),
                    row.getString("event_type"),
                    row.getString("body"),
                    row.getInt("attempts")),
            indexing.current().generation().generation());
    for (var event : events) {
      try {
        kafka
            .send(event.type(), event.documentId().toString(), event.body())
            .get(15, TimeUnit.SECONDS);
        jdbc.update(
            "UPDATE index_outbox SET published_at = now(), last_error = NULL WHERE event_id = ?",
            event.id());
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while publishing index outcome", interrupted);
      } catch (Exception failure) {
        LOG.warn("Index outcome {} could not be published", event.id(), failure);
        long delay = Math.min(60, 1L << Math.min(6, event.attempts()));
        jdbc.update(
            "UPDATE index_outbox SET attempts = attempts + 1, next_attempt_at = ?, last_error = ?"
                + " WHERE event_id = ?",
            java.sql.Timestamp.from(Instant.now().plusSeconds(delay)),
            "Kafka publication failed",
            event.id());
      }
    }
  }

  private record Pending(UUID id, UUID documentId, String type, String body, int attempts) {}
}

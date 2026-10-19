package com.aditya.minsearch.document.application;

import com.aditya.minsearch.shared.domain.event.IndexOutcomePayload;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIndexOutcomeService {
  private final JdbcTemplate jdbc;

  public DocumentIndexOutcomeService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void accept(UUID eventId, IndexOutcomePayload outcome) {
    var documents =
        jdbc.query(
            "SELECT current_version, status, storage_key, deleted_at FROM documents WHERE id = ?"
                + " FOR UPDATE",
            (row, number) ->
                new Current(
                    row.getLong("current_version"),
                    row.getString("status"),
                    row.getString("storage_key"),
                    row.getTimestamp("deleted_at") != null),
            outcome.documentId());
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM processed_events WHERE event_id = ?)",
            Boolean.class,
            eventId))) return;
    if (!documents.isEmpty()) {
      var current = documents.get(0);
      if (current.version() == outcome.version()) {
        switch (outcome.outcome()) {
          case INDEXED -> {
            if (!current.deleted())
              jdbc.update(
                  "UPDATE documents SET status = 'INDEXED', indexing_error = NULL, updated_at ="
                      + " now() WHERE id = ?",
                  outcome.documentId());
          }
          case INDEX_FAILED -> {
            // A late failure for a successfully indexed version must not downgrade its status.
            if (!current.deleted() && !current.status().equals("INDEXED")) {
              jdbc.update(
                  "UPDATE documents SET status = 'INDEX_FAILED', indexing_error = ?, updated_at ="
                      + " now() WHERE id = ?",
                  outcome.error(),
                  outcome.documentId());
            }
          }
          case DELETED -> {
            if (current.deleted() && current.status().equals("DELETE_PENDING")) {
              jdbc.update(
                  """
                  INSERT INTO document_cleanup_jobs(document_id, document_version, storage_key)
                  VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                  """,
                  outcome.documentId(),
                  outcome.version(),
                  current.storageKey());
            }
          }
        }
      }
    }
    jdbc.update(
        "INSERT INTO processed_events(event_id, processed_at) VALUES (?, now()) ON CONFLICT DO"
            + " NOTHING",
        eventId);
  }

  private record Current(long version, String status, String storageKey, boolean deleted) {}
}

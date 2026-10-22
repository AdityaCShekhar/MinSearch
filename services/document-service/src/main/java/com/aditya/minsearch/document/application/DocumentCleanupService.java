package com.aditya.minsearch.document.application;

import com.aditya.minsearch.document.domain.storage.DocumentStorage;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentCleanupService {
  private static final Logger LOG = LoggerFactory.getLogger(DocumentCleanupService.class);
  private final JdbcTemplate jdbc;
  private final DocumentStorage storage;
  private final Clock clock;

  public DocumentCleanupService(JdbcTemplate jdbc, DocumentStorage storage, Clock clock) {
    this.jdbc = jdbc;
    this.storage = storage;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void clean(UUID documentId, long version) {
    // Use the same document-first lock order as updates, deletes, and outcome consumption.
    var documents =
        jdbc.query(
            "SELECT current_version, storage_key, status, deleted_at FROM documents WHERE id = ?"
                + " FOR UPDATE",
            (row, number) ->
                new Current(
                    row.getLong("current_version"),
                    row.getString("storage_key"),
                    row.getString("status"),
                    row.getTimestamp("deleted_at") != null),
            documentId);
    var jobs =
        jdbc.query(
            """
            SELECT storage_key, attempts FROM document_cleanup_jobs
            WHERE document_id = ? AND document_version = ? AND completed_at IS NULL AND next_attempt_at <= now()
            FOR UPDATE
            """,
            (row, number) -> new Job(row.getString("storage_key"), row.getInt("attempts")),
            documentId,
            version);
    if (jobs.isEmpty()) return;
    var job = jobs.get(0);
    boolean eligible =
        !documents.isEmpty()
            && documents.get(0).version() == version
            && documents.get(0).deleted()
            && documents.get(0).status().equals("DELETE_PENDING")
            && documents.get(0).storageKey().equals(job.storageKey());
    if (!eligible) {
      jdbc.update(
          "UPDATE document_cleanup_jobs SET completed_at = now(), last_error = 'Obsolete cleanup"
              + " request' WHERE document_id = ? AND document_version = ?",
          documentId,
          version);
      return;
    }
    try {
      // delete() is idempotent if a crash removed the file before this transaction committed.
      var versions=jdbc.queryForList("SELECT DISTINCT storage_key FROM document_versions WHERE document_id=?", String.class, documentId);
      storage.delete(job.storageKey());
      for (String key : versions) storage.delete(key);
    } catch (Exception failure) {
      LOG.warn("Cleanup failed for document {} version {}", documentId, version, failure);
      long delay = Math.min(60, 1L << Math.min(6, job.attempts()));
      jdbc.update(
          """
          UPDATE document_cleanup_jobs SET attempts = attempts + 1, next_attempt_at = ?, last_error = ?
          WHERE document_id = ? AND document_version = ?
          """,
          Timestamp.from(clock.instant().plusSeconds(delay)),
          "Stored file could not be deleted",
          documentId,
          version);
      return;
    }
    jdbc.update(
        "UPDATE documents SET status = 'DELETED', indexing_error = NULL, updated_at = now() WHERE"
            + " id = ?",
        documentId);
    jdbc.update(
        "UPDATE document_cleanup_jobs SET completed_at = now(), last_error = NULL WHERE document_id"
            + " = ? AND document_version = ?",
        documentId,
        version);
  }

  private record Current(long version, String storageKey, String status, boolean deleted) {}

  private record Job(String storageKey, int attempts) {}
}

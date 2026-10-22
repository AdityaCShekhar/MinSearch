package com.aditya.minsearch.document.infrastructure.messaging;

import com.aditya.minsearch.document.application.DocumentCleanupService;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "minsearch.messaging.enabled", havingValue = "true")
public class DocumentCleanupScheduler {
  private com.aditya.minsearch.shared.infrastructure.WorkerCoordinator workers =
      new com.aditya.minsearch.shared.infrastructure.WorkerCoordinator(java.time.Duration.ofSeconds(25));
  @org.springframework.beans.factory.annotation.Autowired
  void workers(com.aditya.minsearch.shared.infrastructure.WorkerCoordinator workers) { this.workers=workers; }

  private final JdbcTemplate jdbc;
  private final DocumentCleanupService cleanup;

  public DocumentCleanupScheduler(JdbcTemplate jdbc, DocumentCleanupService cleanup) {
    this.jdbc = jdbc;
    this.cleanup = cleanup;
  }

  @Scheduled(fixedDelayString = "${minsearch.messaging.cleanup-delay-ms:1000}")
  public void cleanPending() {
    try (var lease=workers.acquire()) { cleanAdmitted(); }
  }

  private void cleanAdmitted() {
    var pending =
        jdbc.query(
            """
            SELECT document_id, document_version FROM document_cleanup_jobs
            WHERE completed_at IS NULL AND next_attempt_at <= now() ORDER BY next_attempt_at LIMIT 100
            """,
            (row, number) ->
                new Pending(
                    row.getObject("document_id", UUID.class), row.getLong("document_version")));
    for (var job : pending) {
      if (workers.stopping()) break;
      cleanup.clean(job.id(), job.version());
    }
  }

  private record Pending(UUID id, long version) {}
}

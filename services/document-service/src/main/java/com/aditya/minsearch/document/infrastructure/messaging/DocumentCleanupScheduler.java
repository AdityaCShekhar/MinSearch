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
  private final JdbcTemplate jdbc;
  private final DocumentCleanupService cleanup;

  public DocumentCleanupScheduler(JdbcTemplate jdbc, DocumentCleanupService cleanup) {
    this.jdbc = jdbc;
    this.cleanup = cleanup;
  }

  @Scheduled(fixedDelayString = "${minsearch.messaging.cleanup-delay-ms:1000}")
  public void cleanPending() {
    var pending =
        jdbc.query(
            """
            SELECT document_id, document_version FROM document_cleanup_jobs
            WHERE completed_at IS NULL AND next_attempt_at <= now() ORDER BY next_attempt_at LIMIT 100
            """,
            (row, number) ->
                new Pending(
                    row.getObject("document_id", UUID.class), row.getLong("document_version")));
    for (var job : pending) cleanup.clean(job.id(), job.version());
  }

  private record Pending(UUID id, long version) {}
}

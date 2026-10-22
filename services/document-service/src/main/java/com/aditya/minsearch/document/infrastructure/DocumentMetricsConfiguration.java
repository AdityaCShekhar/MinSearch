package com.aditya.minsearch.document.infrastructure;

import com.aditya.minsearch.shared.infrastructure.metrics.DatabaseGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DocumentMetricsConfiguration {
  @Bean MeterBinder documentDatabaseMetrics(JdbcTemplate jdbc) {
    return registry -> {
      for (String status : java.util.List.of("UPLOADED", "INDEX_PENDING", "INDEXING", "INDEXED", "INDEX_FAILED", "DELETE_PENDING", "DELETED"))
        DatabaseGauge.register(registry, jdbc, "minsearch.documents.status", "SELECT count(*) FROM documents WHERE status='"+status+"'", "status", status);
      DatabaseGauge.register(registry, jdbc, "minsearch.documents.bytes", "SELECT coalesce(sum(size_bytes),0) FROM documents WHERE deleted_at IS NULL");
      DatabaseGauge.register(registry, jdbc, "minsearch.outbox.pending", "SELECT count(*) FROM document_outbox WHERE published_at IS NULL", "queue", "document");
      DatabaseGauge.register(registry, jdbc, "minsearch.outbox.oldest.seconds", "SELECT coalesce(extract(epoch from now()-min(occurred_at)),0) FROM document_outbox WHERE published_at IS NULL", "queue", "document");
      DatabaseGauge.register(registry, jdbc, "minsearch.outbox.retry.attempts", "SELECT coalesce(sum(attempts),0) FROM document_outbox", "queue", "document");
      DatabaseGauge.register(registry, jdbc, "minsearch.cleanup.pending", "SELECT count(*) FROM document_cleanup_jobs WHERE completed_at IS NULL");
    };
  }
}

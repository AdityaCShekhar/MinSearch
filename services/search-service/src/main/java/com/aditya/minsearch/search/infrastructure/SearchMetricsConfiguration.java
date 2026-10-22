package com.aditya.minsearch.search.infrastructure;

import com.aditya.minsearch.search.application.DocumentIndexingService;
import com.aditya.minsearch.search.application.SearchService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchMetricsConfiguration {
  @Bean MeterBinder outcomeQueueMetrics(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    return registry -> {
      com.aditya.minsearch.shared.infrastructure.metrics.DatabaseGauge.register(registry, jdbc, "minsearch.outbox.pending", "SELECT count(*) FROM index_outbox WHERE published_at IS NULL", "queue", "index-outcome");
      com.aditya.minsearch.shared.infrastructure.metrics.DatabaseGauge.register(registry, jdbc, "minsearch.outbox.oldest.seconds", "SELECT coalesce(extract(epoch from now()-min(created_at)),0) FROM index_outbox WHERE published_at IS NULL", "queue", "index-outcome");
      com.aditya.minsearch.shared.infrastructure.metrics.DatabaseGauge.register(registry, jdbc, "minsearch.outbox.retry.attempts", "SELECT coalesce(sum(attempts),0) FROM index_outbox", "queue", "index-outcome");
    };
  }
  @Bean
  MeterBinder searchMetrics(SearchService search, DocumentIndexingService index) {
    return registry -> {
      FunctionCounter.builder("minsearch.search.cache.hits", search, service -> service.cacheStats().get("hits"))
          .description("Successful search cache lookups").register(registry);
      FunctionCounter.builder("minsearch.search.cache.misses", search, service -> service.cacheStats().get("misses"))
          .description("Search cache misses").register(registry);
      Gauge.builder("minsearch.index.documents", index, service -> service.current().documents().size())
          .description("Visible documents in the published generation").register(registry);
      Gauge.builder("minsearch.index.generation", index, service -> service.current().generation().generation())
          .register(registry);
      Gauge.builder("minsearch.index.terms", index, service -> service.current().generation().termCount()).register(registry);
      Gauge.builder("minsearch.index.postings", index, service -> service.current().generation().postings().values().stream()
          .mapToLong(list -> list.postings().size()).sum()).register(registry);
      Gauge.builder("minsearch.index.positions", index, service -> service.current().generation().postings().values().stream()
          .flatMap(list -> list.postings().stream()).mapToLong(posting -> posting.termFrequency()).sum()).register(registry);
      Gauge.builder("minsearch.index.payload.estimated.bytes", index, service -> service.current().generation().postings().entrySet().stream()
          .mapToLong(entry -> 2L * entry.getKey().length() + entry.getValue().postings().stream()
              .mapToLong(posting -> 64L + 4L * posting.termFrequency()).sum()).sum())
          .description("Estimate of term/position payload bytes; excludes maps, generations and JVM overhead").register(registry);
    };
  }
}

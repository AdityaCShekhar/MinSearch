package com.aditya.minsearch.shared.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;

/** Scrape-time operational gauges. Queries and labels must be fixed deployment constants. */
public final class DatabaseGauge {
  private DatabaseGauge() {}
  public static void register(MeterRegistry registry, JdbcTemplate jdbc, String name, String sql, String... tags) {
    io.micrometer.core.instrument.Gauge.builder(name, jdbc, database -> {
      try { var value=database.queryForObject(sql, Number.class); return value == null ? 0 : value.doubleValue(); }
      catch (RuntimeException unavailable) { return Double.NaN; }
    }).tags(tags).register(registry);
  }
}

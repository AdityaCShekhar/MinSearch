package com.aditya.minsearch.search.infrastructure;

import com.aditya.minsearch.search.application.DocumentIndexingService;
import org.springframework.boot.health.contributor.*;
import org.springframework.stereotype.Component;

@Component("indexHealthIndicator")
public final class IndexHealthIndicator implements HealthIndicator {
  private final DocumentIndexingService indexing;
  public IndexHealthIndicator(DocumentIndexingService indexing) { this.indexing=indexing; }
  @Override public Health health() { return indexing.recovered() ? Health.up().build() : Health.down().build(); }
}

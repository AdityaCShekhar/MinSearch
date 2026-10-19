package com.aditya.minsearch.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SearchAnalyticsServiceTest {
  @Test void recordsSearchAsynchronouslyAndDoesNotPropagateStorageFailure() {
    CountDownLatch attempted=new CountDownLatch(1);
    JdbcTemplate jdbc=new JdbcTemplate() {
      @Override public int update(String sql,Object... args) { attempted.countDown(); throw new IllegalStateException("database unavailable"); }
    };
    SearchAnalyticsService service=new SearchAnalyticsService(jdbc);
    service.recordAsync(UUID.randomUUID(),"  Hello   WORLD ",3,12,true);
    try { assertThat(attempted.await(2,TimeUnit.SECONDS)).isTrue(); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
  }
}

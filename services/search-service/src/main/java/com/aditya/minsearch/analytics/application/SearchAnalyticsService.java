package com.aditya.minsearch.analytics.application;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public final class SearchAnalyticsService implements org.springframework.context.ApplicationListener<org.springframework.context.event.ContextClosedEvent> {
  private final JdbcTemplate jdbc;
  private final java.util.concurrent.ThreadPoolExecutor executor = new java.util.concurrent.ThreadPoolExecutor(
      2, 2, 0, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1000),
      task -> { Thread thread=new Thread(task, "search-analytics"); thread.setDaemon(true); return thread; },
      new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
  private io.micrometer.core.instrument.MeterRegistry metrics = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
  @org.springframework.beans.factory.annotation.Autowired
  void metrics(io.micrometer.core.instrument.MeterRegistry metrics) { this.metrics=metrics; }
  public SearchAnalyticsService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public void recordAsync(UUID user, String query, long resultCount, long latency, boolean succeeded) {
    if (user == null) return;
    String bounded=query == null ? "" : query.substring(0, Math.min(2048, query.length()));
    try {
      executor.execute(() -> { try { jdbc.update("insert into search_history(id,user_id,query,normalized_query,result_count,latency_ms,succeeded,searched_at) values (?,?,?,?,?,?,?,?)", UUID.randomUUID(),user,bounded,normalize(bounded),resultCount,(int)Math.min(Integer.MAX_VALUE,Math.max(0,latency)),succeeded,java.sql.Timestamp.from(Instant.now())); }
        catch (RuntimeException ignored) { metrics.counter("minsearch.analytics.dropped", "reason", "storage").increment(); } });
    } catch (java.util.concurrent.RejectedExecutionException fullOrStopping) { metrics.counter("minsearch.analytics.dropped", "reason", "capacity-or-shutdown").increment(); }
  }
  @Override public void onApplicationEvent(org.springframework.context.event.ContextClosedEvent event) { executor.shutdown(); }
  @jakarta.annotation.PreDestroy
  public void close() {
    executor.shutdown();
    try { if (!executor.awaitTermination(25, java.util.concurrent.TimeUnit.SECONDS)) executor.shutdownNow(); }
    catch (InterruptedException interrupted) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
  }
  public List<Map<String,Object>> history(UUID user, int limit) { return jdbc.queryForList("select id,query,result_count,latency_ms,succeeded,searched_at from search_history where user_id=? order by searched_at desc limit ?",user,Math.min(100,Math.max(1,limit))); }
  public int deleteHistory(UUID user) { return jdbc.update("delete from search_history where user_id=?",user); }
  public List<Map<String,Object>> trending(int hours, int limit) { return jdbc.queryForList("select normalized_query as query,count(*) as searches from search_history where succeeded=true and searched_at >= now() - (? * interval '1 hour') group by normalized_query order by searches desc,query asc limit ?",Math.min(720,Math.max(1,hours)),Math.min(100,Math.max(1,limit))); }
  public Map<String,Object> aggregate(int hours) { return jdbc.queryForMap("select count(*) filter (where succeeded=false) as failed_queries, coalesce(avg(latency_ms),0) as average_latency_ms from search_history where searched_at >= now() - (? * interval '1 hour')",Math.min(720,Math.max(1,hours))); }
  private static String normalize(String q) { return q == null ? "" : q.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," "); }
}

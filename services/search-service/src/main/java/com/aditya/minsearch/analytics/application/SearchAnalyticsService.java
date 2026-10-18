package com.aditya.minsearch.analytics.application;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public final class SearchAnalyticsService {
  private final JdbcTemplate jdbc;
  public SearchAnalyticsService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public void recordAsync(UUID user, String query, long resultCount, long latency, boolean succeeded) {
    if (user == null) return;
    CompletableFuture.runAsync(() -> { try { jdbc.update("insert into search_history(id,user_id,query,normalized_query,result_count,latency_ms,succeeded,searched_at) values (?,?,?,?,?,?,?,?)", UUID.randomUUID(),user,query,normalize(query),resultCount,(int)Math.min(Integer.MAX_VALUE,Math.max(0,latency)),succeeded,Instant.now()); } catch (RuntimeException ignored) { /* Analytics must not affect search. */ } });
  }
  public List<Map<String,Object>> history(UUID user, int limit) { return jdbc.queryForList("select id,query,result_count,latency_ms,succeeded,searched_at from search_history where user_id=? order by searched_at desc limit ?",user,Math.min(100,Math.max(1,limit))); }
  public int deleteHistory(UUID user) { return jdbc.update("delete from search_history where user_id=?",user); }
  public List<Map<String,Object>> trending(int hours, int limit) { return jdbc.queryForList("select normalized_query as query,count(*) as searches from search_history where succeeded=true and searched_at >= now() - (? * interval '1 hour') group by normalized_query order by searches desc,query asc limit ?",Math.min(720,Math.max(1,hours)),Math.min(100,Math.max(1,limit))); }
  public Map<String,Object> aggregate(int hours) { return jdbc.queryForMap("select count(*) filter (where succeeded=false) as failed_queries, coalesce(avg(latency_ms),0) as average_latency_ms from search_history where searched_at >= now() - (? * interval '1 hour')",Math.min(720,Math.max(1,hours))); }
  private static String normalize(String q) { return q == null ? "" : q.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," "); }
}

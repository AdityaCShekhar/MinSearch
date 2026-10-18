package com.aditya.minsearch.analytics.api;

import com.aditya.minsearch.analytics.application.SearchAnalyticsService;
import com.aditya.minsearch.search.application.SearchService;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public final class SearchHistoryController {
  private final SearchAnalyticsService analytics;
  private final SearchService search;

  public SearchHistoryController(SearchAnalyticsService analytics, SearchService search) {
    this.analytics = analytics;
    this.search = search;
  }

  @GetMapping("/search-history")
  public List<Map<String, Object>> history(
      @AuthenticationPrincipal Jwt principal, @RequestParam(defaultValue = "20") int limit) {
    return analytics.history(UUID.fromString(principal.getSubject()), limit);
  }

  @DeleteMapping("/search-history")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt principal) {
    analytics.deleteHistory(UUID.fromString(principal.getSubject()));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/analytics/trending")
  public List<Map<String, Object>> trending(
      @RequestParam(defaultValue = "24") int hours, @RequestParam(defaultValue = "10") int limit) {
    return analytics.trending(hours, limit);
  }

  @GetMapping("/analytics/metrics")
  public Map<String, Object> metrics(@RequestParam(defaultValue = "24") int hours) {
    Map<String, Object> result = new HashMap<>(analytics.aggregate(hours));
    Map<String, Long> cache = search.cacheStats();
    long total = cache.get("hits") + cache.get("misses");
    result.put("cache_hit_rate", total == 0 ? 0.0 : (double) cache.get("hits") / total);
    return result;
  }
}

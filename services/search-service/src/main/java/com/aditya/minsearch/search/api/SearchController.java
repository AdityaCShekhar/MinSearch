package com.aditya.minsearch.search.api;

import com.aditya.minsearch.analytics.application.SearchAnalyticsService;
import com.aditya.minsearch.autocomplete.application.AutocompleteService;
import com.aditya.minsearch.search.application.DocumentIndexingService;
import com.aditya.minsearch.search.application.SearchPage;
import com.aditya.minsearch.search.application.SearchService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
  private final SearchService searchService;
  private final SearchAnalyticsService analytics;
  private final AutocompleteService autocomplete;
  private final DocumentIndexingService indexing;
  private final com.aditya.minsearch.search.application.SearchPresentationService presentation;
  private io.micrometer.core.instrument.MeterRegistry metrics = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
  @org.springframework.beans.factory.annotation.Autowired
  void metrics(io.micrometer.core.instrument.MeterRegistry metrics) { this.metrics=metrics; }

  public SearchController(
      SearchService searchService,
      SearchAnalyticsService analytics,
      AutocompleteService autocomplete,
      DocumentIndexingService indexing,
      com.aditya.minsearch.search.application.SearchPresentationService presentation) {
    this.searchService = searchService;
    this.analytics = analytics;
    this.autocomplete = autocomplete;
    this.indexing = indexing;
    this.presentation=presentation;
  }

  @PostMapping
  public ResponseEntity<SearchPage> search(
      @RequestBody SearchRequest request, @AuthenticationPrincipal Jwt principal) {
    UUID owner = UUID.fromString(principal.getSubject());
    var snapshot = indexing.current();
    long started = System.nanoTime();
    String outcome = "success";
    try {
      SearchPage result =
          searchService.search(
              request.query(),
              snapshot.generation(),
              snapshot.documents(),
              "ADMIN".equals(principal.getClaimAsString("role")) ? null : owner,
              request.page() == null ? 0 : request.page(),
              request.size() == null ? 20 : request.size());
      analytics.recordAsync(
          owner, request.query(), result.total(), (System.nanoTime() - started) / 1_000_000, true);
      autocomplete.recordQuery(request.query(), owner);
      metrics.summary("minsearch.search.results").record(result.total());
      return ResponseEntity.ok(presentation.present(result,request.query(),snapshot));
    } catch (RuntimeException failure) {
      outcome = "error";
      analytics.recordAsync(
          owner, request.query(), 0, (System.nanoTime() - started) / 1_000_000, false);
      throw failure;
    } finally {
      metrics.timer("minsearch.search.duration", "outcome", outcome, "queryType", queryType(request.query()))
          .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
  }

  public record SearchRequest(String query, Integer page, Integer size) {}

  private static String queryType(String query) {
    if (query == null) return "invalid";
    if (query.contains("~")) return "fuzzy";
    if (query.contains("*")) return "prefix";
    if (query.contains("\"")) return "phrase";
    if (query.contains(":")) return "filtered";
    if (query.contains("(") || query.matches("(?is).*\\b(AND|OR|NOT)\\b.*")) return "boolean";
    return "terms";
  }
}

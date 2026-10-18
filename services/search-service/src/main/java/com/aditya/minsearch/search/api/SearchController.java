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

  public SearchController(
      SearchService searchService,
      SearchAnalyticsService analytics,
      AutocompleteService autocomplete,
      DocumentIndexingService indexing) {
    this.searchService = searchService;
    this.analytics = analytics;
    this.autocomplete = autocomplete;
    this.indexing = indexing;
  }

  @PostMapping
  public ResponseEntity<SearchPage> search(
      @RequestBody SearchRequest request, @AuthenticationPrincipal Jwt principal) {
    UUID owner = UUID.fromString(principal.getSubject());
    var snapshot = indexing.current();
    long started = System.nanoTime();
    try {
      SearchPage result =
          searchService.search(
              request.query(),
              snapshot.generation(),
              snapshot.documents(),
              owner,
              request.page() == null ? 0 : request.page(),
              request.size() == null ? 20 : request.size());
      analytics.recordAsync(
          owner, request.query(), result.total(), (System.nanoTime() - started) / 1_000_000, true);
      autocomplete.recordQuery(request.query(), owner);
      return ResponseEntity.ok(result);
    } catch (RuntimeException failure) {
      analytics.recordAsync(
          owner, request.query(), 0, (System.nanoTime() - started) / 1_000_000, false);
      throw failure;
    }
  }

  public record SearchRequest(String query, Integer page, Integer size) {}
}

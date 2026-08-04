package com.aditya.minsearch.search.api;

import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.search.application.SearchPage;
import com.aditya.minsearch.search.application.SearchService;
import com.aditya.minsearch.search.domain.SearchDocument;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
  private final SearchService searchService;
  private volatile IndexGeneration generation = IndexGeneration.empty();
  private volatile Map<UUID, SearchDocument> documents = Map.of();

  public SearchController(SearchService searchService) { this.searchService = searchService; }

  public void publish(IndexGeneration nextGeneration, Map<UUID, SearchDocument> searchableDocuments) {
    this.generation = nextGeneration;
    this.documents = Map.copyOf(searchableDocuments);
  }

  @PostMapping
  public ResponseEntity<SearchPage> search(@RequestBody SearchRequest request,
      @RequestHeader(value = "X-Owner-Id", required = false) UUID owner) {
    return ResponseEntity.ok(searchService.search(request.query(), generation, documents, owner,
        request.page() == null ? 0 : request.page(), request.size() == null ? 20 : request.size()));
  }

  public record SearchRequest(String query, Integer page, Integer size) {}
}

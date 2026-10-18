package com.aditya.minsearch.autocomplete.api;

import com.aditya.minsearch.autocomplete.application.AutocompleteService;
import com.aditya.minsearch.autocomplete.application.SpellingSuggestionService;
import com.aditya.minsearch.search.application.DocumentIndexingService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public final class DiscoveryController {
  private final AutocompleteService autocomplete;
  private final SpellingSuggestionService suggestions;
  private final DocumentIndexingService indexing;

  public DiscoveryController(
      AutocompleteService autocomplete,
      SpellingSuggestionService suggestions,
      DocumentIndexingService indexing) {
    this.autocomplete = autocomplete;
    this.suggestions = suggestions;
    this.indexing = indexing;
  }

  @GetMapping("/autocomplete")
  public Map<String, List<String>> complete(
      @RequestParam String prefix,
      @RequestParam(defaultValue = "10") int limit,
      @AuthenticationPrincipal Jwt principal) {
    UUID owner = UUID.fromString(principal.getSubject());
    return Map.of(
        "completions",
        autocomplete.complete(prefix, indexing.current().generationForOwner(owner), limit, owner));
  }

  @GetMapping("/suggest")
  public Map<String, String> suggest(
      @RequestParam String term, @AuthenticationPrincipal Jwt principal) {
    UUID owner = UUID.fromString(principal.getSubject());
    return suggestions
        .suggest(term, indexing.current().generationForOwner(owner))
        .map(s -> Map.of("suggestion", s))
        .orElseGet(Map::of);
  }
}

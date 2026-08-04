package com.aditya.minsearch.search.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aditya.minsearch.index.application.IndexGenerationBuilder;
import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.search.domain.SearchDocument;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchServiceTest {
  @Test
  void ranksByTfIdfAndPaginatesDeterministically() {
    UUID first = UUID.randomUUID(); UUID second = UUID.randomUUID();
    IndexGeneration generation = new IndexGenerationBuilder(IndexGeneration.empty())
        .replace(first, 1, List.of(token("term", 0), token("term", 1)))
        .replace(second, 1, List.of(token("term", 0))).build(4);
    Map<UUID, SearchDocument> documents = Map.of(first, doc(first), second, doc(second));

    SearchPage page = new SearchService().search("term", generation, documents, null, 0, 1);

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.results()).extracting(SearchResult::documentId).containsExactly(first);
    assertThat(page.generation()).isEqualTo(4);
  }

  private static TextToken token(String value, int position) { return new TextToken(value, position, 0, value.length()); }
  private static SearchDocument doc(UUID id) { return new SearchDocument(id, UUID.randomUUID(), "en", "txt", Instant.parse("2026-01-01T00:00:00Z")); }
}

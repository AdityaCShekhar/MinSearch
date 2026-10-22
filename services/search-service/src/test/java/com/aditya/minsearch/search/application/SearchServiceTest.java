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
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    IndexGeneration generation =
        new IndexGenerationBuilder(IndexGeneration.empty())
            .replace(first, 1, List.of(token("term", 0), token("term", 1)))
            .replace(second, 1, List.of(token("term", 0)))
            .build(4);
    Map<UUID, SearchDocument> documents = Map.of(first, doc(first), second, doc(second));

    SearchPage page = new SearchService().search("term", generation, documents, null, 0, 1);

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.results()).extracting(SearchResult::documentId).containsExactly(first);
    assertThat(page.generation()).isEqualTo(4);
  }

  private static TextToken token(String value, int position) {
    return new TextToken(value, position, 0, value.length());
  }

  @Test
  void boundedRankingMatchesFullSortAcrossTiesOwnersPagesAndOverflow() {
    var random = new java.util.Random(20261020);
    var builder = new IndexGenerationBuilder(IndexGeneration.empty());
    var metadata = new java.util.HashMap<UUID, SearchDocument>();
    var frequencies = new java.util.HashMap<UUID, Integer>();
    UUID owner = new UUID(1, 1);
    UUID other = new UUID(2, 2);
    for (int i = 0; i < 240; i++) {
      UUID id = new UUID(random.nextLong(), random.nextLong());
      int frequency = random.nextInt(5);
      var tokens = new java.util.ArrayList<TextToken>();
      for (int p = 0; p < frequency; p++) tokens.add(token("term", p));
      builder.replace(id, 1, tokens);
      frequencies.put(id, frequency);
      metadata.put(
          id,
          new SearchDocument(
              id, i % 3 == 0 ? other : owner, "en", "txt", Instant.EPOCH.plusSeconds(i % 7)));
    }
    var generation = builder.build(7);
    double idf =
        Math.log(
                (generation.documentCount() + 1.0)
                    / (generation.postingsFor("term").postings().size() + 1.0))
            + 1;
    var service = new SearchService();
    for (UUID visibleOwner : java.util.Arrays.asList(null, owner, other)) {
      var expected =
          frequencies.entrySet().stream()
              .filter(
                  entry ->
                      entry.getValue() > 0
                          && (visibleOwner == null
                              || metadata.get(entry.getKey()).ownerId().equals(visibleOwner)))
              .map(entry -> new SearchResult(entry.getKey(), (1 + Math.log(entry.getValue())) * idf + 0.5))
              .sorted(
                  java.util.Comparator.comparingDouble(SearchResult::score)
                      .reversed()
                      .thenComparing(
                          java.util.Comparator.comparing(
                                  (SearchResult row) -> metadata.get(row.documentId()).uploadedAt())
                              .reversed())
                      .thenComparing(SearchResult::documentId))
              .toList();
      for (int size : new int[] {1, 7, 20, 100}) {
        for (int page : new int[] {0, 1, 4, 12, Integer.MAX_VALUE}) {
          int from = (int) Math.min((long) page * size, expected.size());
          int to = Math.min(from + size, expected.size());
          var actual = service.search("term", generation, metadata, visibleOwner, page, size);
          assertThat(actual.results()).containsExactlyElementsOf(expected.subList(from, to));
          assertThat(actual.total()).isEqualTo(expected.size());
        }
      }
    }
  }

  private static SearchDocument doc(UUID id) {
    return new SearchDocument(
        id, UUID.randomUUID(), "en", "txt", Instant.parse("2026-01-01T00:00:00Z"));
  }
}

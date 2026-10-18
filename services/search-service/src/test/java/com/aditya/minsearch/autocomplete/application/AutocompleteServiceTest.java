package com.aditya.minsearch.autocomplete.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aditya.minsearch.index.application.IndexGenerationBuilder;
import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.domain.IndexGeneration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutocompleteServiceTest {
  @Test
  void completesVocabularyAndRanksPopularQueries() {
    IndexGeneration generation =
        new IndexGenerationBuilder(IndexGeneration.empty())
            .replace(
                UUID.randomUUID(),
                1,
                List.of(
                    new TextToken("spring", 0, 0, 6),
                    new TextToken("spring", 1, 7, 13),
                    new TextToken("sprout", 2, 14, 20)))
            .build(1);
    AutocompleteService service = new AutocompleteService();
    service.recordQuery("spring guide");
    service.recordQuery("spring guide");
    service.recordQuery("spring boot");
    assertThat(service.complete("spr", generation, 10))
        .contains("spring", "spring guide", "spring boot", "sprout")
        .startsWith("spring guide");
  }

  @Test
  void capsCompletionsAtTen() {
    AutocompleteService service = new AutocompleteService();
    for (int i = 0; i < 12; i++) service.recordQuery("pre word" + i);
    assertThat(service.complete("pre", IndexGeneration.empty(), 20)).hasSize(10);
  }

  @Test
  void doesNotSharePopularQueriesOrCachedResultsBetweenOwners() {
    AutocompleteService service = new AutocompleteService();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    service.recordQuery("private project", first);
    assertThat(service.complete("priv", IndexGeneration.empty(), 10, first))
        .containsExactly("private project");
    assertThat(service.complete("priv", IndexGeneration.empty(), 10, second)).isEmpty();
  }
}

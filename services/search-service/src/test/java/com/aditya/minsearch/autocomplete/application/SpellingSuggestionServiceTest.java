package com.aditya.minsearch.autocomplete.application;

import static org.assertj.core.api.Assertions.assertThat;
import com.aditya.minsearch.index.application.IndexGenerationBuilder;
import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.domain.IndexGeneration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpellingSuggestionServiceTest {
  @Test void returnsExistingTermAndBestCandidate() {
    IndexGeneration generation = new IndexGenerationBuilder(IndexGeneration.empty())
        .replace(UUID.randomUUID(),1,List.of(new TextToken("search",0,0,6),new TextToken("search",1,7,13),new TextToken("search",2,14,20),new TextToken("sear",3,21,25)))
        .build(1);
    SpellingSuggestionService service = new SpellingSuggestionService();
    assertThat(service.suggest("search",generation)).contains("search");
    assertThat(service.suggest("serch",generation)).contains("search");
    assertThat(service.suggest("zzzzzz",generation)).isEmpty();
  }
}

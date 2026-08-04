package com.aditya.minsearch.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aditya.minsearch.index.application.IndexGenerationBuilder;
import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.search.domain.SearchDocument;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueryEvaluatorTest {
  @Test
  void evaluatesBooleanTermsPhrasesPrefixFuzzyAndFilters() {
    UUID first = UUID.randomUUID(); UUID second = UUID.randomUUID();
    IndexGeneration generation = new IndexGenerationBuilder(IndexGeneration.empty())
        .replace(first, 1, List.of(token("spring", 0), token("boot", 1), token("spring", 3)))
        .replace(second, 1, List.of(token("spring", 0), token("kafka", 1))).build(1);
    UUID owner = UUID.randomUUID();
    Map<UUID, SearchDocument> documents = Map.of(first, new SearchDocument(first, owner, "en", "pdf", Instant.now()),
        second, new SearchDocument(second, UUID.randomUUID(), "en", "txt", Instant.now()));
    QueryEvaluator evaluator = new QueryEvaluator();

    assertThat(evaluator.evaluate(new QueryParser().parse("\"spring boot\""), generation, documents, null)).containsExactly(first);
    assertThat(evaluator.evaluate(new QueryParser().parse("spr*"), generation, documents, null)).containsExactlyInAnyOrder(first, second);
    assertThat(evaluator.evaluate(new QueryParser().parse("sprng~"), generation, documents, null)).containsExactlyInAnyOrder(first, second);
    assertThat(evaluator.evaluate(new QueryParser().parse("language:en type:pdf"), generation, documents, null)).containsExactly(first);
  }

  @Test
  void enforcesOwnerAuthorizationAndRejectsStandaloneNot() {
    UUID owner = UUID.randomUUID(); UUID other = UUID.randomUUID(); UUID document = UUID.randomUUID();
    IndexGeneration generation = new IndexGenerationBuilder(IndexGeneration.empty())
        .replace(document, 1, List.of(token("term", 0))).build(1);
    Map<UUID, SearchDocument> documents = Map.of(document, new SearchDocument(document, other, "en", "txt", Instant.now()));
    QueryEvaluator evaluator = new QueryEvaluator();

    assertThat(evaluator.evaluate(new QueryParser().parse("term"), generation, documents, owner)).isEmpty();
    assertThatThrownBy(() -> evaluator.evaluate(new QueryParser().parse("NOT term"), generation, documents, null))
        .isInstanceOf(QuerySyntaxException.class);
  }

  private static TextToken token(String term, int position) { return new TextToken(term, position, 0, term.length()); }
}

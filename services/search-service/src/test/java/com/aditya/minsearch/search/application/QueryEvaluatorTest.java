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
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    IndexGeneration generation =
        new IndexGenerationBuilder(IndexGeneration.empty())
            .replace(first, 1, List.of(token("spring", 0), token("boot", 1), token("spring", 3)))
            .replace(second, 1, List.of(token("spring", 0), token("kafka", 1)))
            .build(1);
    UUID owner = UUID.randomUUID();
    Map<UUID, SearchDocument> documents =
        Map.of(
            first,
            new SearchDocument(first, owner, "en", "pdf", Instant.now()),
            second,
            new SearchDocument(second, UUID.randomUUID(), "en", "txt", Instant.now()));
    QueryEvaluator evaluator = new QueryEvaluator();

    assertThat(
            evaluator.evaluate(
                new QueryParser().parse("\"spring boot\""), generation, documents, null))
        .containsExactly(first);
    assertThat(evaluator.evaluate(new QueryParser().parse("spr*"), generation, documents, null))
        .containsExactlyInAnyOrder(first, second);
    assertThat(evaluator.evaluate(new QueryParser().parse("sprng~"), generation, documents, null))
        .containsExactlyInAnyOrder(first, second);
    assertThat(
            evaluator.evaluate(
                new QueryParser().parse("language:en type:pdf"), generation, documents, null))
        .containsExactly(first);
  }

  @Test
  void enforcesOwnerAuthorizationAndRejectsStandaloneNot() {
    UUID owner = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    UUID document = UUID.randomUUID();
    IndexGeneration generation =
        new IndexGenerationBuilder(IndexGeneration.empty())
            .replace(document, 1, List.of(token("term", 0)))
            .build(1);
    Map<UUID, SearchDocument> documents =
        Map.of(document, new SearchDocument(document, other, "en", "txt", Instant.now()));
    QueryEvaluator evaluator = new QueryEvaluator();

    assertThat(evaluator.evaluate(new QueryParser().parse("term"), generation, documents, owner))
        .isEmpty();
    assertThatThrownBy(
            () ->
                evaluator.evaluate(
                    new QueryParser().parse("NOT term"), generation, documents, null))
        .isInstanceOf(QuerySyntaxException.class);
  }

  private static TextToken token(String term, int position) {
    return new TextToken(term, position, 0, term.length());
  }

  @Test
  void fuzzyRowsMatchAFullMatrixReferenceAndPrefixExpansionRemainsBounded() {
    var words = new java.util.ArrayList<String>();
    for (int length = 1; length <= 4; length++) {
      for (int bits = 0; bits < (1 << length); bits++) {
        StringBuilder word = new StringBuilder();
        for (int bit = 0; bit < length; bit++) word.append((bits & (1 << bit)) == 0 ? 'a' : 'b');
        words.add(word.toString());
      }
    }
    var builder = new IndexGenerationBuilder(IndexGeneration.empty());
    var metadata = new java.util.HashMap<UUID, SearchDocument>();
    var identifiers = new java.util.HashMap<String, UUID>();
    for (String word : words) {
      UUID id = UUID.randomUUID();
      identifiers.put(word, id);
      builder.replace(id, 1, List.of(token(word, 0)));
      metadata.put(id, new SearchDocument(id, id, "en", "txt", Instant.EPOCH));
    }
    var generation = builder.build(1);
    var evaluator = new QueryEvaluator();
    for (String query : words) {
      var expected =
          words.stream()
              .filter(word -> referenceDistance(word, query) <= 2)
              .map(identifiers::get)
              .toList();
      assertThat(
              evaluator.evaluate(
                  new com.aditya.minsearch.search.domain.FuzzyNode(query),
                  generation,
                  metadata,
                  null))
          .containsExactlyInAnyOrderElementsOf(expected);
    }
    for (int i = 0; i < 150; i++) {
      String term = "prefix" + String.format(java.util.Locale.ROOT, "%03d", i);
      UUID id = UUID.randomUUID();
      identifiers.put(term, id);
      builder.replace(id, 1, List.of(token(term, 0)));
      metadata.put(id, new SearchDocument(id, id, "en", "txt", Instant.EPOCH));
    }
    generation = builder.build(2);
    var expected =
        identifiers.keySet().stream()
            .filter(word -> word.startsWith("prefix"))
            .sorted()
            .limit(100)
            .map(identifiers::get)
            .toList();
    assertThat(
            evaluator.evaluate(
                new com.aditya.minsearch.search.domain.PrefixNode("prefix"),
                generation,
                metadata,
                null))
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void authorizesBooleanComplementsAndFiltersAgainstTheSameSnapshot() {
    UUID owner = UUID.randomUUID();
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), hidden = UUID.randomUUID();
    var tokenizer = new com.aditya.minsearch.index.application.TextTokenizer();
    var generation =
        new IndexGenerationBuilder(IndexGeneration.empty())
            .replace(a, 1, tokenizer.tokenize("alpha beta"))
            .replace(b, 1, tokenizer.tokenize("alpha gamma"))
            .replace(hidden, 1, tokenizer.tokenize("alpha beta gamma"))
            .build(1);
    var metadata =
        Map.of(
            a,
            new SearchDocument(a, owner, "en", "txt", Instant.EPOCH),
            b,
            new SearchDocument(b, owner, "fr", "txt", Instant.EPOCH),
            hidden,
            new SearchDocument(hidden, UUID.randomUUID(), "fr", "txt", Instant.EPOCH));
    var evaluator = new QueryEvaluator();
    assertThat(
            evaluator.evaluate(
                new QueryParser().parse("alpha NOT beta"), generation, metadata, owner))
        .containsExactly(b);
    assertThat(
            evaluator.evaluate(
                new QueryParser().parse("alpha NOT (beta OR gamma)"),
                generation,
                metadata,
                owner))
        .isEmpty();
    assertThat(
            evaluator.evaluate(
                new QueryParser().parse("language:fr OR beta"), generation, metadata, owner))
        .containsExactlyInAnyOrder(a, b);
    assertThat(
            evaluator.evaluate(
                new QueryParser().parse("\"alpha beta\" OR gamma"), generation, metadata, owner))
        .containsExactlyInAnyOrder(a, b);
  }

  private int referenceDistance(String left, String right) {
    int[][] matrix = new int[left.length() + 1][right.length() + 1];
    for (int i = 0; i <= left.length(); i++) matrix[i][0] = i;
    for (int j = 0; j <= right.length(); j++) matrix[0][j] = j;
    for (int i = 1; i <= left.length(); i++) {
      for (int j = 1; j <= right.length(); j++)
        matrix[i][j] =
            Math.min(
                Math.min(matrix[i - 1][j] + 1, matrix[i][j - 1] + 1),
                matrix[i - 1][j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
    }
    return matrix[left.length()][right.length()];
  }
}

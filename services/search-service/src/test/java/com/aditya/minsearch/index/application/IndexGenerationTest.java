package com.aditya.minsearch.index.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aditya.minsearch.index.domain.IndexGeneration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndexGenerationTest {
  @Test
  void buildsSortedPositionalPostingsAndRetainsPreviousGeneration() {
    UUID document = UUID.randomUUID();
    IndexGeneration first = new IndexGenerationBuilder(IndexGeneration.empty())
        .replace(document, 1, java.util.List.of(
            new TextToken("term", 2, 0, 4), new TextToken("term", 5, 5, 9)))
        .build(1);
    IndexGeneration second = new IndexGenerationBuilder(first)
        .replace(UUID.randomUUID(), 1, java.util.List.of(new TextToken("term", 1, 0, 4)))
        .build(2);

    assertThat(first.generation()).isOne();
    assertThat(first.postingsFor("term").forDocument(document).termFrequency()).isEqualTo(2);
    assertThat(second.generation()).isEqualTo(2);
    assertThat(first.documents()).hasSize(1);
    assertThat(second.documents()).hasSize(2);
  }

  @Test
  void replacementAndDeletionRemoveOldPostings() {
    UUID document = UUID.randomUUID();
    IndexGeneration generation = new IndexGenerationBuilder(IndexGeneration.empty())
        .replace(document, 1, java.util.List.of(new TextToken("old", 0, 0, 3)))
        .replace(document, 2, java.util.List.of(new TextToken("new", 0, 0, 3)))
        .delete(document, 3)
        .build(3);

    assertThat(generation.postingsFor("old").postings()).isEmpty();
    assertThat(generation.postingsFor("new").postings()).isEmpty();
    assertThat(generation.document(document).deleted()).isTrue();
  }
}

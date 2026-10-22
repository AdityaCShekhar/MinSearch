package com.aditya.minsearch.index.application;

import static org.assertj.core.api.Assertions.*;

import com.aditya.minsearch.index.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PostingLookupTest {
  @Test
  void binaryLookupMatchesTheReferenceScanAcrossSignedUuidBoundaries() {
    Random random = new Random(42);
    var postings = new ArrayList<Posting>();
    for (int i = 0; i < 1000; i++)
      postings.add(new Posting(new UUID(random.nextLong(), random.nextLong()), 1, List.of(0)));
    PostingList list = new PostingList(postings);
    for (Posting posting : postings)
      assertThat(list.forDocument(posting.documentId())).isSameAs(posting);
    assertThat(list.forDocument(new UUID(0, 0))).isNull();
    assertThat(list.forDocument(null)).isNull();
    assertThat(new PostingList(List.of()).forDocument(UUID.randomUUID())).isNull();
  }

  @Test
  void bulkInsertionStillReplacesAndDeletesExistingDocuments() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    var builder = new IndexGenerationBuilder(IndexGeneration.empty());
    builder.replace(first, 1, List.of(new TextToken("old", 0, 0, 3)));
    builder.replace(second, 1, List.of(new TextToken("retained", 0, 0, 8)));
    builder.replace(first, 2, List.of(new TextToken("new", 0, 0, 3)));
    var generation = builder.build(1);
    assertThat(generation.postings()).doesNotContainKey("old");
    assertThat(generation.postingsFor("new").forDocument(first).documentVersion()).isEqualTo(2);
    assertThat(generation.postingsFor("retained").forDocument(second)).isNotNull();
    assertThat(builder.delete(first, 3).build(2).postings()).containsOnlyKeys("retained");
  }
}

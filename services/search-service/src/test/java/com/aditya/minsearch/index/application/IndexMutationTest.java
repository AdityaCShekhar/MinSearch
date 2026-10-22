package com.aditya.minsearch.index.application;

import static org.assertj.core.api.Assertions.*;

import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.index.domain.Posting;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndexMutationTest {
  @Test
  void sharesUntouchedPostingListsAndRetainsPublishedGenerationsAcrossMutations() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var source =
        new IndexGenerationBuilder(IndexGeneration.empty())
            .replace(a, 1, List.of(token("old"), token("shared")))
            .replace(b, 1, List.of(token("unrelated")))
            .build(1);
    var builder = new IndexGenerationBuilder(source);
    var next = builder.replace(a, 2, List.of(token("new"))).build(2);
    assertThat(next.postingsFor("unrelated")).isSameAs(source.postingsFor("unrelated"));
    assertThat(next.postings()).doesNotContainKeys("old", "shared");
    assertThat(source.postings()).containsKeys("old", "shared").doesNotContainKey("new");
    var deleted = builder.delete(a, 3).build(3);
    assertThat(deleted.postings()).containsOnlyKeys("unrelated");
    assertThat(next.postings()).containsKey("new");
    assertThat(deleted.document(a).deleted()).isTrue();
    assertThat(deleted.documentCount()).isOne();
    var restored = builder.replace(a, 4, List.of(token("restored"))).build(4);
    assertThat(restored.postings()).containsOnlyKeys("restored", "unrelated");
    assertThat(restored.documentCount()).isEqualTo(2);
  }

  @Test
  void packedPositionsPreserveListSemanticsAndDefensiveCopies() {
    var single = new Posting(UUID.randomUUID(), 1, List.of(2048));
    assertThat(single.positionAt(0)).isEqualTo(2048);
    assertThat(single.hasPosition(2048)).isTrue();
    assertThat(single.hasPosition(2047)).isFalse();
    var pair = new Posting(UUID.randomUUID(), 1, List.of(0, 2048));
    assertThat(pair.positionAt(1)).isEqualTo(2048);
    assertThat(pair.hasPosition(2048)).isTrue();
    assertThat(pair.hasPosition(2047)).isFalse();
    var input = new ArrayList<>(List.of(0, 128, 4096));
    var posting = new Posting(UUID.randomUUID(), 1, input);
    input.set(0, 100);
    assertThat(posting.positions()).containsExactly(0, 128, 4096);
    assertThat(posting.positions().contains(4096)).isTrue();
    assertThat(posting.positions().contains(4095)).isFalse();
    assertThat(posting.positions()).isEqualTo(List.of(0, 128, 4096));
    assertThat(posting).isEqualTo(new Posting(posting.documentId(), 1, List.of(0, 128, 4096)));
    assertThatThrownBy(() -> posting.positions().set(0, 3))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new Posting(UUID.randomUUID(), 1, List.of(1, 1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Posting(UUID.randomUUID(), 1, List.of(2, 1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Posting(UUID.randomUUID(), 1, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private TextToken token(String term) {
    return new TextToken(term, 0, 0, term.length());
  }
}

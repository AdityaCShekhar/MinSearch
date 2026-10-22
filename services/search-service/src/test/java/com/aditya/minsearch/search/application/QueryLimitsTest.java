package com.aditya.minsearch.search.application;

import static org.assertj.core.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class QueryLimitsTest {
  @Test void rejectsLengthTokenNestingAndNotRecursionLimits() {
    for (String input : new String[]{"a".repeat(2049), "word ".repeat(101),
        "(".repeat(11)+"term"+")".repeat(11), "NOT ".repeat(11)+"term"}) {
      assertThatThrownBy(() -> new QueryParser().parse(input)).isInstanceOf(QuerySyntaxException.class);
    }
    assertThatCode(() -> new QueryParser().parse("word ".repeat(100))).doesNotThrowAnyException();
    var limits = new QueryLimits(12, 2, 1, 3, 2, 1);
    assertThatThrownBy(() -> new QueryParser(limits).parse("a b c")).isInstanceOf(QuerySyntaxException.class);
  }
  @Test void arbitraryInputTerminatesWithAstOrPositionedSyntaxError() {
    var random = new Random(778);
    String alphabet = "abc NOT OR AND()\"*:~_-0123";
    for (int run=0;run<2000;run++) {
      var text = new StringBuilder();
      for (int i=random.nextInt(200); i>0; i--) text.append(alphabet.charAt(random.nextInt(alphabet.length())));
      try { new QueryParser().parse(text.toString()); }
      catch (QuerySyntaxException error) { assertThat(error.position()).isBetween(0, text.length()); }
    }
  }
}

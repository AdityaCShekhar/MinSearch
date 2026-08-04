package com.aditya.minsearch.search.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SnippetRendererTest {
  private final SnippetRenderer renderer = new SnippetRenderer();

  @Test
  void escapesOriginalTextAndHighlightsCaseInsensitiveMatches() {
    String snippet = renderer.render("<script>alert('Spring')</script> boot", List.of("spring"), 240);

    assertThat(snippet).isEqualTo("&lt;script&gt;alert(&#39;<mark>Spring</mark>&#39;)&lt;/script&gt; boot");
    assertThat(snippet).doesNotContain("<script>");
  }

  @Test
  void centersAWindowOnTheDensestPhraseAndAddsEllipses() {
    String text = "prefix text that is intentionally long and distant phrase spring boot suffix content";

    String snippet = renderer.render(text, List.of("spring", "boot"), List.of("spring boot"), 30);

    assertThat(snippet).contains("<mark>spring boot</mark>").startsWith("…").endsWith("…");
  }

  @Test
  void enforcesTheHardMaximum() {
    String text = "x".repeat(600);

    assertThat(renderer.render(text, List.of(), 10_000).length()).isLessThanOrEqualTo(501);
  }
}

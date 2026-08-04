package com.aditya.minsearch.index.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextTokenizerTest {
  private final TextTokenizer tokenizer = new TextTokenizer();

  @Test
  void normalizesCompatibilityCharactersAndUsesRootCaseRules() {
    assertThat(tokenizer.normalize("ＦＩLE ﬁ İ Σ"))
        .isEqualTo("file fi i̇ σ");
  }

  @Test
  void tokenizesLettersNumbersAndCombiningMarksDeterministically() {
    List<TextToken> tokens = tokenizer.tokenize("Café, v2.0 — naïve");

    assertThat(tokens).extracting(TextToken::term).containsExactly("café", "v2", "0", "naïve");
    assertThat(tokens).extracting(TextToken::position).containsExactly(0, 1, 2, 3);
    assertThat(tokens.get(0).startOffset()).isZero();
    assertThat(tokens.get(0).endOffset()).isEqualTo(4);
  }

  @Test
  void treatsPunctuationAndSymbolsAsBoundariesAndDoesNotEmitEmptyTokens() {
    assertThat(tokenizer.tokenize("  hello/world + \uD83D\uDD0E  "))
        .extracting(TextToken::term).containsExactly("hello", "world");
    assertThat(tokenizer.tokenize(null)).isEmpty();
  }
}

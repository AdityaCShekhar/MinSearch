package com.aditya.minsearch.index.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageAwareTokenProcessorTest {
  @Test
  void removesEnglishStopWordsWhilePreservingOriginalPositions() {
    TextTokenizer tokenizer = new TextTokenizer();
    var processed = new LanguageAwareTokenProcessor(new EnglishStopWordFilter(), new EnglishStemmer())
        .process("en", tokenizer.tokenize("The running foxes"));

    assertThat(processed).extracting(TextToken::term).containsExactly("run", "fox");
    assertThat(processed).extracting(TextToken::position).containsExactly(1, 2);
  }

  @Test
  void leavesUnsupportedLanguagesUnfilteredAndUnstemmed() {
    var token = new TextToken("running", 0, 0, 7);
    var processed = new LanguageAwareTokenProcessor(new EnglishStopWordFilter(), new EnglishStemmer())
        .process("fr", java.util.List.of(token));

    assertThat(processed).containsExactly(token);
  }
}

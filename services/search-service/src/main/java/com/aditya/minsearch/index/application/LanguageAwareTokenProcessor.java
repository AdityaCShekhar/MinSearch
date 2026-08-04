package com.aditya.minsearch.index.application;

import java.util.List;

public final class LanguageAwareTokenProcessor {
  private final StopWordFilter stopWords;
  private final Stemmer stemmer;

  public LanguageAwareTokenProcessor(StopWordFilter stopWords, Stemmer stemmer) {
    this.stopWords = stopWords;
    this.stemmer = stemmer;
  }

  public List<TextToken> process(String language, List<TextToken> tokens) {
    return tokens.stream()
        .filter(token -> !stopWords.supports(language) || !stopWords.isStopWord(token.term()))
        .map(token -> stemmer.supports(language)
            ? new TextToken(stemmer.stem(token.term()), token.position(), token.startOffset(), token.endOffset())
            : token)
        .toList();
  }
}

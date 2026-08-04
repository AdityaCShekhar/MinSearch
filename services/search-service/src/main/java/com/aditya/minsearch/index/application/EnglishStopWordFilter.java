package com.aditya.minsearch.index.application;

import java.util.Set;

public final class EnglishStopWordFilter implements StopWordFilter {
  private static final Set<String> WORDS = Set.of(
      "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "if",
      "in", "into", "is", "it", "no", "not", "of", "on", "or", "such", "that", "the",
      "their", "then", "there", "these", "they", "this", "to", "was", "we", "were", "what",
      "when", "where", "which", "who", "will", "with", "you");

  @Override
  public boolean supports(String language) {
    return language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("en");
  }

  @Override
  public boolean isStopWord(String term) {
    return WORDS.contains(term);
  }
}

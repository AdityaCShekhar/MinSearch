package com.aditya.minsearch.index.application;

import java.util.Locale;

public final class EnglishStemmer implements Stemmer {
  @Override
  public boolean supports(String language) {
    return language != null && language.toLowerCase(Locale.ROOT).startsWith("en");
  }

  @Override
  public String stem(String term) {
    if (term.length() < 3) return term;
    String word = term;
    if (word.endsWith("sses")) return word.substring(0, word.length() - 2);
    if (word.endsWith("ies")) return word.substring(0, word.length() - 2);
    if (word.endsWith("xes") || word.endsWith("zes") || word.endsWith("ches") || word.endsWith("shes")) {
      word = word.substring(0, word.length() - 2);
    }
    if (word.endsWith("ss")) return word;
    if (word.endsWith("s")) word = word.substring(0, word.length() - 1);
    if (word.endsWith("eed")) return word.substring(0, word.length() - 1);
    if (word.endsWith("ed") && hasVowel(word, word.length() - 2)) word = word.substring(0, word.length() - 2);
    else if (word.endsWith("ing") && hasVowel(word, word.length() - 3)) word = word.substring(0, word.length() - 3);
    if (word.length() > 2 && word.charAt(word.length() - 1) == word.charAt(word.length() - 2)
        && "b d f g m n p r t".indexOf(word.charAt(word.length() - 1)) >= 0) {
      word = word.substring(0, word.length() - 1);
    }
    if (word.endsWith("ational")) word = word.substring(0, word.length() - 5) + "e";
    else if (word.endsWith("tional")) word = word.substring(0, word.length() - 2);
    else if (word.endsWith("izer")) word = word.substring(0, word.length() - 1);
    else if (word.endsWith("fulness")) word = word.substring(0, word.length() - 4);
    else if (word.endsWith("ousness")) word = word.substring(0, word.length() - 4);
    else if (word.endsWith("ment")) word = word.substring(0, word.length() - 4);
    if (word.endsWith("y") && hasVowel(word, word.length() - 1)) word = word.substring(0, word.length() - 1) + "i";
    return word;
  }

  private boolean hasVowel(String word, int endExclusive) {
    for (int i = 0; i < endExclusive; i++) if ("aeiou".indexOf(word.charAt(i)) >= 0) return true;
    return false;
  }
}

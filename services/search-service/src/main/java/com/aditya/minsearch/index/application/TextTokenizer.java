package com.aditya.minsearch.index.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextTokenizer {
  private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}\\p{M}]+");

  public String normalize(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }

  public List<TextToken> tokenize(String text) {
    String normalized = normalize(text);
    Matcher matcher = TOKEN.matcher(normalized);
    List<TextToken> tokens = new ArrayList<>();
    int position = 0;
    while (matcher.find()) {
      tokens.add(new TextToken(matcher.group(), position++, matcher.start(), matcher.end()));
    }
    return List.copyOf(tokens);
  }
}

package com.aditya.minsearch.index.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextTokenizer {
  private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}\\p{M}]+");
  private final int maximumTokens;
  private final int maximumTermLength;
  public TextTokenizer() { this(Integer.MAX_VALUE, Integer.MAX_VALUE); }
  public TextTokenizer(int maximumTokens, int maximumTermLength) {
    if (maximumTokens < 1 || maximumTermLength < 1) throw new IllegalArgumentException("Token limits must be positive");
    this.maximumTokens = maximumTokens;
    this.maximumTermLength = maximumTermLength;
  }

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
      if (tokens.size() >= maximumTokens || matcher.end() - matcher.start() > maximumTermLength)
        throw new IllegalArgumentException("Document token or term-length limit exceeded");
      tokens.add(new TextToken(matcher.group(), position++, matcher.start(), matcher.end()));
    }
    return List.copyOf(tokens);
  }
}

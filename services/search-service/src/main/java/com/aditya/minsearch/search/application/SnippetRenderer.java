package com.aditya.minsearch.search.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SnippetRenderer {
  public static final int DEFAULT_LENGTH = 240;
  public static final int MAXIMUM_LENGTH = 500;

  public String render(String text, List<String> terms, List<String> phrases, int requestedLength) {
    if (text == null || text.isEmpty()) return "";
    int length = Math.max(1, Math.min(requestedLength, MAXIMUM_LENGTH));
    List<Span> spans = matches(text, phrases, true);
    spans.addAll(matches(text, terms, false));
    spans = distinctSpans(spans);
    if (spans.isEmpty()) return escape(text.substring(0, Math.min(length, text.length())));

    Span anchor = bestAnchor(spans, length);
    int start = Math.max(0, Math.min(anchor.start() - (length - anchor.length()) / 2, text.length() - length));
    int end = Math.min(text.length(), start + length);
    StringBuilder output = new StringBuilder();
    if (start > 0) output.append("…");
    int cursor = start;
    for (Span span : spans) {
      if (span.end() <= start || span.start() >= end) continue;
      int spanStart = Math.max(start, span.start());
      int spanEnd = Math.min(end, span.end());
      if (spanStart > cursor) output.append(escape(text.substring(cursor, spanStart)));
      output.append("<mark>").append(escape(text.substring(spanStart, spanEnd))).append("</mark>");
      cursor = spanEnd;
    }
    if (cursor < end) output.append(escape(text.substring(cursor, end)));
    if (end < text.length()) output.append("…");
    return output.toString();
  }

  public String render(String text, List<String> terms, int requestedLength) {
    return render(text, terms, List.of(), requestedLength);
  }

  private List<Span> matches(String text, List<String> values, boolean phrase) {
    List<Span> result = new ArrayList<>();
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      Matcher matcher = Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
      while (matcher.find()) result.add(new Span(matcher.start(), matcher.end(), phrase));
    }
    return result;
  }

  private List<Span> distinctSpans(List<Span> spans) {
    spans.sort(Comparator.comparingInt(Span::start).thenComparingInt(span -> span.phrase() ? 0 : 1));
    List<Span> distinct = new ArrayList<>();
    for (Span candidate : spans) {
      if (distinct.stream().noneMatch(existing -> candidate.start() < existing.end() && existing.start() < candidate.end())) {
        distinct.add(candidate);
      }
    }
    return distinct;
  }

  private Span bestAnchor(List<Span> spans, int length) {
    return spans.stream().max(Comparator.comparingInt((Span anchor) -> {
      int windowEnd = anchor.start() + length;
      return (int) spans.stream().filter(span -> span.start() >= anchor.start() && span.end() <= windowEnd)
          .mapToInt(span -> span.phrase() ? 100 : 1).sum();
    }).thenComparingInt(Span::length).reversed()).orElse(spans.get(0));
  }

  private String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }

  private record Span(int start, int end, boolean phrase) {
    int length() { return end - start; }
  }
}

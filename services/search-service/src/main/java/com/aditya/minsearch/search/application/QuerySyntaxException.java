package com.aditya.minsearch.search.application;

public final class QuerySyntaxException extends IllegalArgumentException implements com.aditya.minsearch.shared.domain.error.PositionedError {
  private final int position;

  public QuerySyntaxException(String message, int position) {
    super(message + " at position " + position);
    this.position = position;
  }

  public int position() { return position; }
}

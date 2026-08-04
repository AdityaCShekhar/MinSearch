package com.aditya.minsearch.search.application;

public final class QuerySyntaxException extends IllegalArgumentException {
  private final int position;

  public QuerySyntaxException(String message, int position) {
    super(message + " at position " + position);
    this.position = position;
  }

  public int position() { return position; }
}

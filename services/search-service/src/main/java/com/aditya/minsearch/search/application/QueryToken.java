package com.aditya.minsearch.search.application;

public record QueryToken(Type type, String lexeme, int start, int end) {
  public enum Type { TERM, PHRASE, AND, OR, NOT, LPAREN, RPAREN, COLON, STAR, FUZZY }
}

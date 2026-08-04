package com.aditya.minsearch.search.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QueryLexer {
  public List<QueryToken> lex(String query) {
    if (query == null) throw new QuerySyntaxException("Query must not be null", 0);
    List<QueryToken> tokens = new ArrayList<>();
    int cursor = 0;
    while (cursor < query.length()) {
      char current = query.charAt(cursor);
      if (Character.isWhitespace(current)) { cursor++; continue; }
      if (current == '"') {
        int start = cursor++;
        StringBuilder phrase = new StringBuilder();
        boolean closed = false;
        while (cursor < query.length()) {
          char character = query.charAt(cursor++);
          if (character == '\\' && cursor < query.length() && query.charAt(cursor) == '"') {
            phrase.append('"'); cursor++; continue;
          }
          if (character == '"') { closed = true; break; }
          phrase.append(character);
        }
        if (!closed) throw new QuerySyntaxException("Unterminated phrase", start);
        if (phrase.isEmpty()) throw new QuerySyntaxException("Empty phrase", start);
        tokens.add(new QueryToken(QueryToken.Type.PHRASE, phrase.toString(), start, cursor));
        continue;
      }
      QueryToken.Type punctuation = switch (current) {
        case '(' -> QueryToken.Type.LPAREN;
        case ')' -> QueryToken.Type.RPAREN;
        case ':' -> QueryToken.Type.COLON;
        case '*' -> QueryToken.Type.STAR;
        case '~' -> QueryToken.Type.FUZZY;
        default -> null;
      };
      if (punctuation != null) {
        tokens.add(new QueryToken(punctuation, String.valueOf(current), cursor, ++cursor));
        continue;
      }
      if (Character.isLetterOrDigit(current) || current == '_' || current == '-' || current == '.') {
        int start = cursor++;
        while (cursor < query.length()) {
          char character = query.charAt(cursor);
          if (!(Character.isLetterOrDigit(character) || character == '_' || character == '-' || character == '.')) break;
          cursor++;
        }
        String lexeme = query.substring(start, cursor);
        QueryToken.Type type = switch (lexeme.toUpperCase(Locale.ROOT)) {
          case "AND" -> QueryToken.Type.AND;
          case "OR" -> QueryToken.Type.OR;
          case "NOT" -> QueryToken.Type.NOT;
          default -> QueryToken.Type.TERM;
        };
        tokens.add(new QueryToken(type, lexeme, start, cursor));
        continue;
      }
      throw new QuerySyntaxException("Unexpected character '" + current + "'", cursor);
    }
    return List.copyOf(tokens);
  }
}

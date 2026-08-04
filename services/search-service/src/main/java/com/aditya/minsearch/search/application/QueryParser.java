package com.aditya.minsearch.search.application;

import com.aditya.minsearch.search.domain.BooleanNode;
import com.aditya.minsearch.search.domain.FilterNode;
import com.aditya.minsearch.search.domain.FuzzyNode;
import com.aditya.minsearch.search.domain.NotNode;
import com.aditya.minsearch.search.domain.PhraseNode;
import com.aditya.minsearch.search.domain.PrefixNode;
import com.aditya.minsearch.search.domain.QueryNode;
import com.aditya.minsearch.search.domain.TermNode;
import java.util.List;

public final class QueryParser {
  private List<QueryToken> tokens;
  private int cursor;

  public QueryNode parse(String query) {
    tokens = new QueryLexer().lex(query);
    cursor = 0;
    if (tokens.isEmpty()) throw new QuerySyntaxException("Query must not be empty", 0);
    QueryNode node = parseOr();
    if (cursor != tokens.size()) throw error("Unexpected token", tokens.get(cursor));
    return node;
  }

  private QueryNode parseOr() {
    QueryNode node = parseAnd();
    while (match(QueryToken.Type.OR)) node = new BooleanNode(node, BooleanNode.Operator.OR, parseAnd());
    return node;
  }

  private QueryNode parseAnd() {
    QueryNode node = parseUnary();
    while (cursor < tokens.size()) {
      if (match(QueryToken.Type.AND)) node = new BooleanNode(node, BooleanNode.Operator.AND, parseUnary());
      else if (match(QueryToken.Type.NOT)) node = new BooleanNode(node, BooleanNode.Operator.NOT, parseUnary());
      else if (startsPrimary(tokens.get(cursor).type())) node = new BooleanNode(node, BooleanNode.Operator.AND, parseUnary());
      else break;
    }
    return node;
  }

  private QueryNode parseUnary() {
    if (match(QueryToken.Type.NOT)) return new NotNode(parseUnary());
    return parsePrimary();
  }

  private QueryNode parsePrimary() {
    if (match(QueryToken.Type.LPAREN)) {
      QueryNode node = parseOr();
      require(QueryToken.Type.RPAREN, "Missing closing parenthesis");
      return node;
    }
    QueryToken token = requireAny("Expected a term, phrase, or parenthesized expression");
    if (token.type() == QueryToken.Type.PHRASE) return new PhraseNode(token.lexeme());
    if (token.type() != QueryToken.Type.TERM) throw error("Expected a term", token);
    if (match(QueryToken.Type.COLON)) {
      QueryToken value = require(QueryToken.Type.TERM, "Expected filter value");
      return new FilterNode(token.lexeme(), value.lexeme());
    }
    if (match(QueryToken.Type.STAR)) return new PrefixNode(token.lexeme());
    if (match(QueryToken.Type.FUZZY)) return new FuzzyNode(token.lexeme());
    return new TermNode(token.lexeme());
  }

  private boolean startsPrimary(QueryToken.Type type) {
    return type == QueryToken.Type.TERM || type == QueryToken.Type.PHRASE || type == QueryToken.Type.LPAREN;
  }

  private boolean match(QueryToken.Type type) {
    if (cursor < tokens.size() && tokens.get(cursor).type() == type) { cursor++; return true; }
    return false;
  }

  private QueryToken require(QueryToken.Type type, String message) {
    if (cursor >= tokens.size() || tokens.get(cursor).type() != type) {
      throw new QuerySyntaxException(message, cursor >= tokens.size() ? -1 : tokens.get(cursor).start());
    }
    return tokens.get(cursor++);
  }

  private QueryToken requireAny(String message) {
    if (cursor >= tokens.size()) throw new QuerySyntaxException(message, -1);
    return tokens.get(cursor++);
  }

  private QuerySyntaxException error(String message, QueryToken token) {
    return new QuerySyntaxException(message, token.start());
  }
}

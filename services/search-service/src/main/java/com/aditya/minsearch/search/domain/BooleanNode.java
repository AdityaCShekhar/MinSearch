package com.aditya.minsearch.search.domain;

public record BooleanNode(QueryNode left, Operator operator, QueryNode right) implements QueryNode {
  public enum Operator { AND, OR, NOT }
}

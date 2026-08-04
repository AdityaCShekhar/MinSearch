package com.aditya.minsearch.search.domain;

public record FilterNode(String field, String value) implements QueryNode {}

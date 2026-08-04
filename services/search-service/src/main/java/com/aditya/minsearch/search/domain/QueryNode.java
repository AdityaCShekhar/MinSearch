package com.aditya.minsearch.search.domain;

public sealed interface QueryNode permits TermNode, PhraseNode, BooleanNode, FilterNode, PrefixNode, FuzzyNode, NotNode {}

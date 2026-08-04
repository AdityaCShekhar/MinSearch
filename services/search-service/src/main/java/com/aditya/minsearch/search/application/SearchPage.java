package com.aditya.minsearch.search.application;

import java.util.List;

public record SearchPage(List<SearchResult> results, int page, int size, long total, long generation) {}

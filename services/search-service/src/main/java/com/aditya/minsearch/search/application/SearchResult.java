package com.aditya.minsearch.search.application;

import java.util.UUID;

public record SearchResult(UUID documentId, double score) {}

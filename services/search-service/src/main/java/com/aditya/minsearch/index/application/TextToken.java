package com.aditya.minsearch.index.application;

public record TextToken(String term, int position, int startOffset, int endOffset) {}

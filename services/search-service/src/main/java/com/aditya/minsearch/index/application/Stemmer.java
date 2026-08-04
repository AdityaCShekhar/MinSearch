package com.aditya.minsearch.index.application;

public interface Stemmer {
  boolean supports(String language);

  String stem(String term);
}

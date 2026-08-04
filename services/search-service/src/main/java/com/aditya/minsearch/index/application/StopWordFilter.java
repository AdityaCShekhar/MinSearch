package com.aditya.minsearch.index.application;

public interface StopWordFilter {
  boolean supports(String language);

  boolean isStopWord(String term);
}

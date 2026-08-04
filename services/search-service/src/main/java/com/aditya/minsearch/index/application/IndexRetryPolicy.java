package com.aditya.minsearch.index.application;

import java.time.Duration;

public record IndexRetryPolicy(int maxAttempts, Duration initialDelay) {
  public IndexRetryPolicy {
    if (maxAttempts <= 0 || initialDelay.isNegative() || initialDelay.isZero()) {
      throw new IllegalArgumentException("Invalid retry policy");
    }
  }

  public Duration delayForAttempt(int attempt) {
    if (attempt <= 0) throw new IllegalArgumentException("Attempt must be positive");
    long multiplier = 1L << Math.min(attempt - 1, 30);
    try { return initialDelay.multipliedBy(multiplier); }
    catch (ArithmeticException overflow) { return Duration.ofSeconds(Long.MAX_VALUE); }
  }
}

package com.aditya.minsearch.index.application;

public record IndexWorkResult(Status status, int attempts, String failureReason) {
  public enum Status { INDEXED, DELETED, DUPLICATE, OUT_OF_ORDER, RETRYABLE_FAILURE, FAILED }
}

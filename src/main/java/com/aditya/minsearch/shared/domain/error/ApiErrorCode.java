package com.aditya.minsearch.shared.domain.error;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
  GENERIC_BAD_REQUEST(
      "GENERIC_BAD_REQUEST",
      "Bad request",
      HttpStatus.BAD_REQUEST,
      "https://minsearch.dev/problems/bad-request"),
  GENERIC_INTERNAL_ERROR(
      "GENERIC_INTERNAL_ERROR",
      "Internal server error",
      HttpStatus.INTERNAL_SERVER_ERROR,
      "https://minsearch.dev/problems/internal-error"),
  VALIDATION_FAILED(
      "VALIDATION_FAILED",
      "Request validation failed",
      HttpStatus.BAD_REQUEST,
      "https://minsearch.dev/problems/validation-failed");

  private final String code;
  private final String title;
  private final HttpStatus status;
  private final String type;

  ApiErrorCode(String code, String title, HttpStatus status, String type) {
    this.code = code;
    this.title = title;
    this.status = status;
    this.type = type;
  }

  public String code() {
    return code;
  }

  public String title() {
    return title;
  }

  public HttpStatus status() {
    return status;
  }

  public String type() {
    return type;
  }
}

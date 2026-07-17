package com.aditya.minsearch.shared.domain.error;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ApiProblem {

  private ApiProblem() {}

  public static ProblemDetail create(
      ApiErrorCode errorCode, String detail, String instance, String correlationId) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
    problemDetail.setTitle(errorCode.title());
    problemDetail.setType(URI.create(errorCode.type()));
    if (instance != null) {
      problemDetail.setInstance(URI.create(instance));
    }
    problemDetail.setProperty("code", errorCode.code());
    if (correlationId != null && !correlationId.isBlank()) {
      problemDetail.setProperty("correlationId", correlationId);
    }
    return problemDetail;
  }

  public static ProblemDetail create(ApiErrorCode errorCode, String detail) {
    return create(errorCode, detail, null, null);
  }

  public static ProblemDetail create(
      HttpStatus status,
      String title,
      String detail,
      String type,
      String instance,
      String code,
      String correlationId) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    problemDetail.setType(URI.create(type));
    if (instance != null) {
      problemDetail.setInstance(URI.create(instance));
    }
    problemDetail.setProperty("code", code);
    if (correlationId != null && !correlationId.isBlank()) {
      problemDetail.setProperty("correlationId", correlationId);
    }
    return problemDetail;
  }
}

package com.aditya.minsearch.shared.infrastructure.web;

import com.aditya.minsearch.shared.domain.error.ApiErrorCode;
import com.aditya.minsearch.shared.domain.error.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Objects;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler({
    BindException.class,
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<ProblemDetail> handleBadRequest(
      Exception exception, HttpServletRequest request) {
    ProblemDetail problemDetail =
        ApiProblem.create(
            ApiErrorCode.VALIDATION_FAILED,
            resolveDetail(exception),
            request.getRequestURI(),
            resolveCorrelationId(request));
    resolvePosition(exception)
        .ifPresent(position -> problemDetail.setProperty("position", position));
    return ResponseEntity.status(problemDetail.getStatus()).body(problemDetail);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(
      Exception exception, HttpServletRequest request) {
    ProblemDetail problemDetail =
        ApiProblem.create(
            ApiErrorCode.GENERIC_INTERNAL_ERROR,
            "An unexpected error occurred",
            request.getRequestURI(),
            resolveCorrelationId(request));
    return ResponseEntity.status(problemDetail.getStatus()).body(problemDetail);
  }

  private static String resolveDetail(Exception exception) {
    if (exception instanceof BindException bindException) {
      return bindException.getAllErrors().stream()
          .findFirst()
          .map(
              error ->
                  error.getDefaultMessage() != null
                      ? error.getDefaultMessage()
                      : "Request validation failed")
          .orElse("Request validation failed");
    }
    if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
      return methodArgumentNotValidException.getBindingResult().getAllErrors().stream()
          .findFirst()
          .map(
              error ->
                  error.getDefaultMessage() != null
                      ? error.getDefaultMessage()
                      : "Request validation failed")
          .orElse("Request validation failed");
    }
    if (exception instanceof ConstraintViolationException violationException) {
      return violationException.getConstraintViolations().stream()
          .findFirst()
          .map(violation -> violation.getMessage())
          .orElse("Request validation failed");
    }
    return Objects.requireNonNullElse(exception.getMessage(), "Request validation failed");
  }

  private static java.util.Optional<Integer> resolvePosition(Exception exception) {
    if (exception instanceof BindException bindException) {
      return bindException.getFieldErrors().stream()
          .map(ApiExceptionHandler::positionFromFieldError)
          .flatMap(java.util.Optional::stream)
          .findFirst();
    }
    if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
      return methodArgumentNotValidException.getBindingResult().getFieldErrors().stream()
          .map(ApiExceptionHandler::positionFromFieldError)
          .flatMap(java.util.Optional::stream)
          .findFirst();
    }
    return java.util.Optional.empty();
  }

  private static java.util.Optional<Integer> positionFromFieldError(FieldError fieldError) {
    Object rejectedValue = fieldError.getRejectedValue();
    if (rejectedValue instanceof Number number) {
      return java.util.Optional.of(number.intValue());
    }
    return java.util.Optional.empty();
  }

  private static String resolveCorrelationId(HttpServletRequest request) {
    Object attribute = request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME);
    if (attribute instanceof String value && !value.isBlank()) {
      return value;
    }
    String correlationId = request.getHeader("X-Correlation-Id");
    return correlationId == null || correlationId.isBlank() ? null : correlationId;
  }
}

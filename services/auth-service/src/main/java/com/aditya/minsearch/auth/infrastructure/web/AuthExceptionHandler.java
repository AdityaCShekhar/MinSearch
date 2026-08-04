package com.aditya.minsearch.auth.infrastructure.web;

import com.aditya.minsearch.auth.domain.InvalidCredentialsException;
import com.aditya.minsearch.auth.domain.TooManyAuthenticationAttemptsException;
import com.aditya.minsearch.shared.domain.error.ApiErrorCode;
import com.aditya.minsearch.shared.domain.error.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ProblemDetail> handleAuthenticationFailure(
			InvalidCredentialsException exception, HttpServletRequest request) {
		ProblemDetail problemDetail =
				ApiProblem.create(
						ApiErrorCode.AUTHENTICATION_FAILED,
						exception.getMessage(),
						request.getRequestURI(),
						null);
		return ResponseEntity.status(problemDetail.getStatus()).body(problemDetail);
	}

	@ExceptionHandler(TooManyAuthenticationAttemptsException.class)
	public ResponseEntity<ProblemDetail> handleRateLimit(
			TooManyAuthenticationAttemptsException exception, HttpServletRequest request) {
		ProblemDetail problemDetail =
				ApiProblem.create(
						ApiErrorCode.TOO_MANY_REQUESTS,
						exception.getMessage(),
						request.getRequestURI(),
						null);
		return ResponseEntity.status(problemDetail.getStatus()).body(problemDetail);
	}
}

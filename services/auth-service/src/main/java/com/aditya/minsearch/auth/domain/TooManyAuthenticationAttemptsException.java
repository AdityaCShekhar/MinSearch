package com.aditya.minsearch.auth.domain;

public class TooManyAuthenticationAttemptsException extends RuntimeException {

	public TooManyAuthenticationAttemptsException() {
		super("Too many authentication attempts");
	}
}

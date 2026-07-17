package com.aditya.minsearch.auth.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegistrationRequest(
		@NotBlank @Email String email, @NotBlank String password) {}

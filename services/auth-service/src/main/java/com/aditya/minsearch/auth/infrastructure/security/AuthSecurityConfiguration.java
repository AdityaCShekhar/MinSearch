package com.aditya.minsearch.auth.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

@Configuration
public class AuthSecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
		http.csrf(csrf -> csrf.disable());
		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.authorizeHttpRequests(
				auth ->
						auth.requestMatchers(
										"/actuator/health/liveness",
										"/actuator/health/readiness",
										"/swagger-ui.html",
										"/swagger-ui/**",
										"/v3/api-docs",
										"/v3/api-docs/**")
								.permitAll()
								.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
								.permitAll()
								.anyRequest()
								.authenticated());
		http.addFilterBefore(new JwtAuthenticationFilter(jwtDecoder), AbstractPreAuthenticatedProcessingFilter.class);
		return http.build();
	}
}

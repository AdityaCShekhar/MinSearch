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
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
			tools.jackson.databind.ObjectMapper mapper,
			@org.springframework.beans.factory.annotation.Value("${minsearch.openapi.public:false}") boolean publicDocs) throws Exception {
		http.csrf(csrf -> csrf.disable());
		http.cors(org.springframework.security.config.Customizer.withDefaults());
		http.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.exceptionHandling(errors -> errors
			.authenticationEntryPoint((request, response, failure) -> com.aditya.minsearch.shared.infrastructure.security.SecurityResponses.reject(mapper, request, response, 401))
			.accessDeniedHandler((request, response, failure) -> com.aditya.minsearch.shared.infrastructure.security.SecurityResponses.reject(mapper, request, response, 403)));
		if (publicDocs) http.authorizeHttpRequests(auth -> auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll());
		http.authorizeHttpRequests(
				auth ->
						auth.requestMatchers(
										"/actuator/health/liveness",
										"/actuator/health/readiness")
								.permitAll()
								.requestMatchers("/actuator/**").hasRole("ADMIN")
								.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
								.permitAll()
								.anyRequest()
								.authenticated());
		http.oauth2ResourceServer(resource -> resource
			.authenticationEntryPoint((request, response, failure) -> com.aditya.minsearch.shared.infrastructure.security.SecurityResponses.reject(mapper, request, response, 401))
			.accessDeniedHandler((request, response, failure) -> com.aditya.minsearch.shared.infrastructure.security.SecurityResponses.reject(mapper, request, response, 403))
			.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(token ->
				new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(token,
					java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + token.getClaimAsString("role")))))));
		return http.build();
	}
}

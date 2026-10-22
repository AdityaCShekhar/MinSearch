package com.aditya.minsearch.shared.infrastructure.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "minsearch.security.resource-server.enabled", havingValue = "true")
public class ResourceSecurityConfiguration {
  @Bean
  JwtDecoder resourceJwtDecoder(@Value("${minsearch.security.jwt.secret}") String secret,
      @Value("${minsearch.security.jwt.previous-secret:}") String previous) {
    return JwtKeys.decoder(secret, previous);
  }

  @Bean
  SecurityFilterChain resourceSecurityFilterChain(HttpSecurity http, tools.jackson.databind.ObjectMapper mapper) throws Exception {
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("role");
    roles.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
    authentication.setJwtGrantedAuthoritiesConverter(roles);
    return http.csrf(csrf -> csrf.disable())
        .cors(org.springframework.security.config.Customizer.withDefaults())
        .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, failure) -> SecurityResponses.reject(mapper, request, response, 401))
            .accessDeniedHandler((request, response, failure) -> SecurityResponses.reject(mapper, request, response, 403)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/actuator/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/analytics/**", "/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resource -> resource
                .authenticationEntryPoint((request, response, failure) -> SecurityResponses.reject(mapper, request, response, 401))
                .accessDeniedHandler((request, response, failure) -> SecurityResponses.reject(mapper, request, response, 403))
                .jwt(jwt -> jwt.jwtAuthenticationConverter(authentication)))
        .build();
  }
}

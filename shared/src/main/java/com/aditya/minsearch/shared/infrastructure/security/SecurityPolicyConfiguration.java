package com.aditya.minsearch.shared.infrastructure.security;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.web.cors.*;

@Configuration
@EnableConfigurationProperties(SecurityPolicy.class)
public class SecurityPolicyConfiguration {
  @Bean CorsConfigurationSource corsConfigurationSource(SecurityPolicy policy) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(policy.corsAllowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
    configuration.setExposedHeaders(List.of("X-Correlation-Id"));
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}

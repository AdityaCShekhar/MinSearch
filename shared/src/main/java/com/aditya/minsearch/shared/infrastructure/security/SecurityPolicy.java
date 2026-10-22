package com.aditya.minsearch.shared.infrastructure.security;

import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("minsearch.security")
public record SecurityPolicy(
    @DefaultValue List<String> corsAllowedOrigins,
    @DefaultValue("1200") @Min(1) @jakarta.validation.constraints.Max(1000000) int requestsPerMinute,
    @DefaultValue("65536") @Min(1024) @jakarta.validation.constraints.Max(1048576) int maxJsonBytes) {
  public SecurityPolicy {
    corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    for (String value : corsAllowedOrigins) {
      var origin = java.net.URI.create(value);
      if (origin.getHost() == null || origin.getUserInfo() != null || origin.getQuery() != null
          || origin.getFragment() != null || (origin.getPath() != null && !origin.getPath().isEmpty())
          || !List.of("http", "https").contains(origin.getScheme()))
        throw new IllegalArgumentException("CORS requires explicit HTTP(S) origins");
    }
  }
}

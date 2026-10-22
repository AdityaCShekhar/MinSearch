package com.aditya.minsearch.shared.infrastructure.security;

import java.util.Arrays;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class DeploymentSecretGuard implements InitializingBean {
  private final Environment environment;
  public DeploymentSecretGuard(Environment environment) { this.environment = environment; }
  @Override public void afterPropertiesSet() {
    if (!"production".equals(environment.getProperty("minsearch.deployment.mode"))
        && !Arrays.asList(environment.getActiveProfiles()).contains("production")) return;
    require("minsearch.security.jwt.secret", 32);
    require("spring.datasource.password", 16);
    if (environment.getProperty("minsearch.cache.redis.enabled", Boolean.class, false))
      require("spring.data.redis.password", 16);
    String previous = environment.getProperty("minsearch.security.jwt.previous-secret", "");
    if (!previous.isBlank()) require("minsearch.security.jwt.previous-secret", 32);
  }
  private void require(String name, int minimum) {
    String value = environment.getProperty(name, "");
    String normalized = value.toLowerCase(java.util.Locale.ROOT);
    if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < minimum
        || normalized.contains("local-only") || normalized.contains("change-this")
        || normalized.contains("minsearch_dev") || normalized.equals("password"))
      throw new IllegalStateException("Production requires an externally configured strong secret: " + name);
  }
}

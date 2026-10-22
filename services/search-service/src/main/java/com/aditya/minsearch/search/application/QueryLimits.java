package com.aditya.minsearch.search.application;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("minsearch.query")
public record QueryLimits(
    @DefaultValue("2048") @Min(1) @Max(8192) int maxLength,
    @DefaultValue("100") @Min(1) @Max(500) int maxTokens,
    @DefaultValue("10") @Min(1) @Max(20) int maxDepth,
    @DefaultValue("100") @Min(1) @Max(1000) int prefixExpansions,
    @DefaultValue("50") @Min(1) @Max(100) int fuzzyExpansions,
    @DefaultValue("2") @Min(0) @Max(2) int fuzzyDistance) {
  public static QueryLimits defaults() { return new QueryLimits(2048,100,10,100,50,2); }
}

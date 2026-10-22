package com.aditya.minsearch.search.application;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("minsearch.ranking")
public record RankingPolicy(
    @DefaultValue("2.0") @DecimalMin("0") @DecimalMax("100") double titleBoost,
    @DefaultValue("3.0") @DecimalMin("0") @DecimalMax("100") double phraseBoost,
    @DefaultValue("0.5") @DecimalMin("0") @DecimalMax("100") double exactTermBoost) {
  public static RankingPolicy defaults() { return new RankingPolicy(2,3,.5); }
}

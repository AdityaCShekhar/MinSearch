package com.aditya.minsearch.document.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentClockConfiguration {
  @Bean Clock clock() { return Clock.systemUTC(); }
}

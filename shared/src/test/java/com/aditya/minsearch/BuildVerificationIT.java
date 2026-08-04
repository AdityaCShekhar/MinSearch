package com.aditya.minsearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BuildVerificationIT {

  @LocalServerPort private int port;

  @Test
  void applicationContextStartsForIntegrationTests() {
    assertThat(port).isPositive();
  }
}

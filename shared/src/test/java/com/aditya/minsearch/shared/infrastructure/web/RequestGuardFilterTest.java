package com.aditya.minsearch.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import com.aditya.minsearch.shared.infrastructure.security.SecurityPolicy;

class RequestGuardFilterTest {
  @Test void boundsChunkedJsonAndPreservesAcceptedBody() throws Exception {
    var guard = new RequestGuardFilter(new SecurityPolicy(List.of(), 20, 1024), (a,b,c,d) -> {});
    var request = request("{\"query\":\"hello\"}");
    var body = new AtomicReference<String>();
    var response = new MockHttpServletResponse();
    guard.doFilter(request, response, (req, res) -> body.set(new String(req.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
    assertThat(body.get()).isEqualTo("{\"query\":\"hello\"}");
    var chunked = new MockHttpServletRequest("POST", "/api/v1/search") {
      @Override public long getContentLengthLong() { return -1; }
      @Override public int getContentLength() { return -1; }
    };
    chunked.setContentType("application/json"); chunked.setContent(new byte[1025]);
    response = new MockHttpServletResponse();
    guard.doFilter(chunked, response, (req, res) -> fail("oversized body reached application"));
    assertThat(response.getStatus()).isEqualTo(413);
  }
  @Test void limitsSubjectAndResetsAfterWindow() throws Exception {
    var now = new AtomicReference<>(Instant.parse("2026-10-20T00:00:00Z"));
    Clock clock = new Clock() {
      public ZoneId getZone() { return ZoneOffset.UTC; }
      public Clock withZone(ZoneId zone) { return this; }
      public Instant instant() { return now.get(); }
    };
    var guard = new RequestGuardFilter(new SecurityPolicy(List.of(), 1, 1024), (a,b,c,d)->{}, clock);
    guard.doFilter(request("{}"), new MockHttpServletResponse(), (req,res)->{});
    var rejected = new MockHttpServletResponse();
    guard.doFilter(request("{}"), rejected, (req,res)->fail("rate limit bypassed"));
    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
    now.set(now.get().plusSeconds(60));
    guard.doFilter(request("{}"), new MockHttpServletResponse(), (req,res)->{});
  }
  private MockHttpServletRequest request(String body) {
    var request = new MockHttpServletRequest("POST", "/api/v1/search");
    request.setContentType("application/json"); request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return request;
  }
}

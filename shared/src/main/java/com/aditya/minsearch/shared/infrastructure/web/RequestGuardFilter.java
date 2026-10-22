package com.aditya.minsearch.shared.infrastructure.web;

import com.aditya.minsearch.shared.infrastructure.security.SecurityPolicy;
import com.github.benmanes.caffeine.cache.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class RequestGuardFilter extends OncePerRequestFilter {
  private final SecurityPolicy policy;
  private final java.time.Clock clock;
  private final com.aditya.minsearch.shared.domain.security.AuditTrail audit;
  private final Cache<String, Bucket> requests = Caffeine.newBuilder().maximumSize(100_000).expireAfterAccess(Duration.ofMinutes(2)).build();
  @org.springframework.beans.factory.annotation.Autowired
  public RequestGuardFilter(SecurityPolicy policy, com.aditya.minsearch.shared.domain.security.AuditTrail audit) { this(policy, audit, java.time.Clock.systemUTC()); }
  RequestGuardFilter(SecurityPolicy policy, com.aditya.minsearch.shared.domain.security.AuditTrail audit, java.time.Clock clock) { this.policy = policy; this.audit = audit; this.clock = clock; }
  private static class Bucket { long minute; int count; synchronized boolean allow(long minute, int limit) {
    if (this.minute != minute) { this.minute = minute; count = 0; } return ++count <= limit;
  }}
  @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/v1/"); }
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    String subject = authentication != null && authentication.isAuthenticated()
        ? authentication.getName() : request.getRemoteAddr();
    long minute = clock.millis() / 60_000;
    if (!requests.get("ip:" + request.getRemoteAddr(), ignored -> new Bucket()).allow(minute, policy.requestsPerMinute() * 5)
        || !requests.get("subject:" + subject, ignored -> new Bucket()).allow(minute, policy.requestsPerMinute())) {
      response.setHeader("Retry-After", "60");
      problem(response, 429, "RATE_LIMITED", "Request rate limit exceeded");
      return;
    }
    if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt
        && "ADMIN".equals(jwt.getClaimAsString("role"))) {
      audit.record(java.util.UUID.fromString(jwt.getSubject()), "admin.http." + request.getMethod(), null, "ATTEMPT");
    }
    String type = request.getContentType();
    if (type != null && (type.startsWith("application/json") || type.startsWith("application/problem+json"))) {
      if (request.getContentLengthLong() > policy.maxJsonBytes()) {
        problem(response, 413, "REQUEST_TOO_LARGE", "JSON request exceeds the configured limit"); return;
      }
      byte[] body = request.getInputStream().readNBytes(policy.maxJsonBytes() + 1);
      if (body.length > policy.maxJsonBytes()) { problem(response, 413, "REQUEST_TOO_LARGE", "JSON request exceeds the configured limit"); return; }
      request = new HttpServletRequestWrapper(request) {
        @Override public ServletInputStream getInputStream() {
          var input = new ByteArrayInputStream(body);
          return new ServletInputStream() {
            public int read() { return input.read(); }
            public int read(byte[] buffer, int offset, int length) { return input.read(buffer, offset, length); }
            public boolean isFinished() { return input.available() == 0; }
            public boolean isReady() { return true; }
            public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous JSON requests only"); }
          };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
      };
    }
    chain.doFilter(request, response);
  }
  private void problem(HttpServletResponse response, int status, String code, String detail) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.getWriter().write("{\"type\":\"https://minsearch.dev/problems/" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')
        + "\",\"title\":\"Request rejected\",\"status\":" + status + ",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
  }
}

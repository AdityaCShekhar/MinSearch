package com.aditya.minsearch.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.UUID;
import com.aditya.minsearch.shared.domain.security.AuditTrail;
import com.aditya.minsearch.shared.infrastructure.web.RequestGuardFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "minsearch.security.resource-server.enabled=true", "minsearch.security.jwt.secret=" + JwtKeysTest.CURRENT,
    "minsearch.security.cors-allowed-origins=https://client.example", "management.endpoints.web.exposure.include=health,metrics,prometheus"})
@Import({ResourceSecurityConfiguration.class, SecurityPolicyConfiguration.class, RequestGuardFilter.class, HttpSecurityTest.Probe.class})
class HttpSecurityTest {
  @LocalServerPort int port;
  @MockitoBean AuditTrail audit;
  private final HttpClient client = HttpClient.newHttpClient();
  @RestController static class Probe {
    @GetMapping({"/api/v1/security-probe", "/api/v1/analytics/security-probe"}) String probe() { return "ok"; }
  }
  @Test void enforcesRealFilterChainCorsHeadersRolesAndAdminAudit() throws Exception {
    var anonymous = send("/api/v1/security-probe", null);
    assertThat(anonymous.statusCode()).isEqualTo(401);
    assertThat(anonymous.body()).contains("AUTHENTICATION_FAILED");
    assertThat(anonymous.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    assertThat(anonymous.headers().firstValue("Content-Security-Policy")).contains("default-src 'none'; frame-ancestors 'none'");
    String user = token("USER"), admin = token("ADMIN");
    assertThat(send("/api/v1/security-probe", user).statusCode()).isEqualTo(200);
    assertThat(send("/api/v1/analytics/security-probe", user).statusCode()).isEqualTo(403);
    assertThat(send("/actuator/metrics", user).statusCode()).isEqualTo(403);
    assertThat(send("/actuator/prometheus", user).statusCode()).isEqualTo(403);
    var prometheus=send("/actuator/prometheus", admin);
    assertThat(prometheus.statusCode()).isEqualTo(200);
    assertThat(prometheus.body()).contains("jvm_memory_used_bytes");
    assertThat(send("/api/v1/analytics/security-probe", admin).statusCode()).isEqualTo(200);
    verify(audit).record(any(UUID.class), eq("admin.http.GET"), isNull(), eq("ATTEMPT"));
    for (String origin : new String[]{"https://client.example", "https://evil.example"}) {
      var response = client.send(HttpRequest.newBuilder(uri("/api/v1/security-probe"))
          .header("Origin", origin).header("Access-Control-Request-Method", "GET")
          .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(origin.contains("evil") ? 403 : 200);
      if (!origin.contains("evil")) assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(origin);
    }
  }
  private String token(String role) { var now=Instant.now(); return JwtKeysTest.token(JwtKeysTest.CURRENT, "minsearch", UUID.randomUUID().toString(), role, now, now.plusSeconds(900)); }
  private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
  private HttpResponse<String> send(String path, String token) throws Exception {
    var request = HttpRequest.newBuilder(uri(path));
    if (token != null) request.header("Authorization", "Bearer " + token);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}

package com.aditya.minsearch.shared.infrastructure.security;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

public final class SecurityResponses {
  private SecurityResponses() {}
  public static void reject(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response, int status) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    response.getWriter().write(mapper.writeValueAsString(Map.of(
        "type", "https://minsearch.dev/problems/" + (status == 401 ? "authentication-failed" : "access-denied"),
        "title", status == 401 ? "Authentication required" : "Access denied",
        "status", status, "code", status == 401 ? "AUTHENTICATION_FAILED" : "ACCESS_DENIED",
        "instance", request.getRequestURI())));
  }
}

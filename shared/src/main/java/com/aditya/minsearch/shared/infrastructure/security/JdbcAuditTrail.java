package com.aditya.minsearch.shared.infrastructure.security;

import com.aditya.minsearch.shared.domain.security.AuditTrail;
import com.aditya.minsearch.shared.infrastructure.web.CorrelationIdFilter;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcAuditTrail implements AuditTrail {
  private final ObjectProvider<JdbcTemplate> jdbc;
  private final String table;
  public JdbcAuditTrail(ObjectProvider<JdbcTemplate> jdbc, @Value("${minsearch.audit.table:security_audit_log}") String table) {
    if (!Set.of("security_audit_log", "auth_security_audit", "document_security_audit", "search_security_audit").contains(table))
      throw new IllegalArgumentException("Unsupported audit table");
    this.jdbc = jdbc;
    this.table = table;
  }
  @Override public void record(UUID actor, String action, UUID resource, String outcome) {
    // No passwords, token values, document contents, filenames, or query text are recorded.
    jdbc.getObject().update("INSERT INTO " + table + "(id,actor_id,action,resource_id,outcome,correlation_id,occurred_at) VALUES (?,?,?,?,?,?,now())",
        UUID.randomUUID(), actor, action, resource, outcome, MDC.get(CorrelationIdFilter.MDC_KEY));
  }
}

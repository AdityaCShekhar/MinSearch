CREATE TABLE search_security_audit (
  id UUID PRIMARY KEY,
  actor_id UUID NOT NULL,
  action VARCHAR(64) NOT NULL,
  resource_id UUID,
  outcome VARCHAR(16) NOT NULL,
  correlation_id VARCHAR(64),
  occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX search_security_audit_actor_time ON search_security_audit(actor_id, occurred_at DESC);

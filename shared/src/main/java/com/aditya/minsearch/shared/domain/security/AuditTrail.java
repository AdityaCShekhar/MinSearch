package com.aditya.minsearch.shared.domain.security;

import java.util.UUID;

public interface AuditTrail {
  void record(UUID actor, String action, UUID resource, String outcome);
}

package com.aditya.minsearch.search.infrastructure;

import com.aditya.minsearch.shared.domain.event.DocumentIndexPayload;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import com.aditya.minsearch.shared.domain.event.IndexOutcomePayload;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class IndexOutcomeOutbox {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public IndexOutcomeOutbox(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(DomainEvent source, DocumentIndexPayload document, String error) {
    UUID id =
        UUID.nameUUIDFromBytes(
            ("index.failed:" + source.eventId()).getBytes(StandardCharsets.UTF_8));
    var payload =
        new IndexOutcomePayload(
            1,
            document.documentId(),
            document.version(),
            0,
            IndexOutcomePayload.Outcome.INDEX_FAILED,
            error);
    var event =
        new DomainEvent(id, "index.failed", Instant.now(), mapper.writeValueAsString(payload));
    // A late failed delivery cannot supersede an already durable successful version.
    jdbc.update(
        """
        INSERT INTO index_outbox(event_id, document_id, event_type, required_generation, body)
        SELECT ?, ?, 'index.failed', 0, ?
        WHERE NOT EXISTS (SELECT 1 FROM search_index_documents WHERE document_id = ? AND document_version >= ?)
        ON CONFLICT (event_id) DO NOTHING
        """,
        id,
        document.documentId(),
        mapper.writeValueAsString(event),
        document.documentId(),
        document.version());
  }
}

package com.aditya.minsearch.search.infrastructure;

import com.aditya.minsearch.search.application.SearchIndexStore;
import com.aditya.minsearch.shared.domain.event.DomainEvent;
import com.aditya.minsearch.shared.domain.event.IndexOutcomePayload;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
@DependsOnDatabaseInitialization
public class JdbcSearchIndexStore implements SearchIndexStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcSearchIndexStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public OptionalLong save(Mutation mutation) {
    List<Long> revisions =
        jdbc.query(
            """
            INSERT INTO search_index_documents(document_id, document_version, revision, mutation)
            VALUES (?, ?, nextval('search_index_revision'), ?)
            ON CONFLICT (document_id) DO UPDATE
            SET document_version = EXCLUDED.document_version, revision = EXCLUDED.revision,
                mutation = EXCLUDED.mutation
            WHERE search_index_documents.document_version < EXCLUDED.document_version
            RETURNING revision
            """,
            (row, number) -> row.getLong("revision"),
            mutation.documentId(),
            mutation.version(),
            mapper.writeValueAsString(new Mutation(mutation.eventId(),mutation.documentId(),mutation.version(),mutation.deleted(),mutation.document(),mutation.tokens())));
    if (revisions.isEmpty()) return OptionalLong.empty();
    if (mutation.text()!=null && !mutation.deleted()) jdbc.update(
        "INSERT INTO search_index_content(document_id,document_version,body) VALUES (?,?,?) ON CONFLICT DO NOTHING",
        mutation.documentId(),mutation.version(),mutation.text());
    if (mutation.deleted()) jdbc.update("DELETE FROM search_index_content WHERE document_id=?",mutation.documentId());
    long revision = revisions.get(0);
    var outcome =
        new IndexOutcomePayload(
            1,
            mutation.documentId(),
            mutation.version(),
            revision,
            mutation.deleted()
                ? IndexOutcomePayload.Outcome.DELETED
                : IndexOutcomePayload.Outcome.INDEXED,
            null);
    UUID eventId =
        UUID.nameUUIDFromBytes(
            ("index.completed:" + mutation.eventId()).getBytes(StandardCharsets.UTF_8));
    var event =
        new DomainEvent(
            eventId, "index.completed", Instant.now(), mapper.writeValueAsString(outcome));
    jdbc.update(
        """
        INSERT INTO index_outbox(event_id, document_id, event_type, required_generation, body)
        VALUES (?, ?, ?, ?, ?)
        """,
        eventId,
        mutation.documentId(),
        event.eventType(),
        revision,
        mapper.writeValueAsString(event));
    return OptionalLong.of(revision);
  }

  @Override
  public List<StoredMutation> load() {
    return jdbc.query(
        "SELECT revision, mutation FROM search_index_documents ORDER BY revision",
        (row, number) ->
            new StoredMutation(
                row.getLong("revision"),
                mapper.readValue(row.getString("mutation"), Mutation.class)));
  }

  @Override
  @Transactional(readOnly = true)
  public void forEach(java.util.function.Consumer<StoredMutation> consumer) {
    // PostgreSQL cursors require a transaction and a positive fetch size. Keep decoded token
    // mutations bounded instead of retaining the entire corpus alongside the reconstructed index.
    jdbc.query(
        connection -> {
          var statement =
              connection.prepareStatement(
                  "SELECT revision, mutation FROM search_index_documents ORDER BY revision");
          statement.setFetchSize(128);
          return statement;
        },
        (org.springframework.jdbc.core.RowCallbackHandler)
            row ->
                consumer.accept(
                    new StoredMutation(
                        row.getLong("revision"),
                        mapper.readValue(row.getString("mutation"), Mutation.class))));
  }
}

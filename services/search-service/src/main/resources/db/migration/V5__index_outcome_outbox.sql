CREATE TABLE index_outbox (
    event_id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    required_generation BIGINT NOT NULL CHECK (required_generation >= 0),
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error TEXT
);

CREATE INDEX idx_index_outbox_pending ON index_outbox(next_attempt_at, created_at)
    WHERE published_at IS NULL;

-- Existing durable projections also need status feedback when this migration is applied.
WITH outcomes AS (
    SELECT gen_random_uuid() AS event_id, document_id, revision,
        json_build_object('schemaVersion', 1, 'documentId', document_id,
            'version', document_version, 'generation', revision,
            'outcome', CASE WHEN (mutation::jsonb ->> 'deleted')::boolean THEN 'DELETED' ELSE 'INDEXED' END,
            'error', NULL)::text AS payload
    FROM search_index_documents
)
INSERT INTO index_outbox(event_id, document_id, event_type, required_generation, body)
SELECT event_id, document_id, 'index.completed', revision,
    json_build_object('eventId', event_id, 'eventType', 'index.completed',
        'occurredAt', now(), 'payload', payload)::text
FROM outcomes;

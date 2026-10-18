ALTER TABLE documents ADD COLUMN indexing_error VARCHAR(512);

CREATE TABLE document_cleanup_jobs (
    document_id UUID NOT NULL REFERENCES documents(id),
    document_version BIGINT NOT NULL CHECK (document_version > 0),
    storage_key VARCHAR(500) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error TEXT,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (document_id, document_version)
);
CREATE INDEX idx_document_cleanup_pending ON document_cleanup_jobs(next_attempt_at)
    WHERE completed_at IS NULL;

CREATE SEQUENCE search_index_revision;

-- Latest tokenized version per document; retained tombstones prevent stale resurrection.
CREATE TABLE search_index_documents (
    document_id UUID PRIMARY KEY,
    document_version BIGINT NOT NULL CHECK (document_version > 0),
    revision BIGINT NOT NULL UNIQUE,
    mutation TEXT NOT NULL
);

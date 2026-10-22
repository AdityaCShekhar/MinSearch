CREATE TABLE search_index_content (
    document_id UUID NOT NULL,
    document_version BIGINT NOT NULL,
    body TEXT NOT NULL,
    PRIMARY KEY(document_id,document_version)
);

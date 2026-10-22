CREATE SEQUENCE document_visibility_revision;
CREATE TABLE document_visibility (
    document_id UUID PRIMARY KEY REFERENCES documents(id),
    revision BIGINT NOT NULL DEFAULT nextval('document_visibility_revision') UNIQUE
);
INSERT INTO document_visibility(document_id) SELECT id FROM documents WHERE deleted_at IS NOT NULL;
INSERT INTO document_versions(id,document_id,version,storage_key,checksum,size_bytes,title,language,created_at)
SELECT gen_random_uuid(),id,current_version,storage_key,checksum,size_bytes,title,language,updated_at
FROM documents ON CONFLICT(document_id,version) DO NOTHING;

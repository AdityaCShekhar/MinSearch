-- Search applies versioned deletion events to its own index. It no longer reads
-- document-service tables; retain the original V4 migration checksum for upgrades.
DROP TABLE document_visibility;
DROP SEQUENCE document_visibility_revision;

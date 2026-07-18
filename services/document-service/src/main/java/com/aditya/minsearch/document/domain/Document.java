package com.aditya.minsearch.document.domain;

import java.time.Instant;
import java.util.UUID;

public record Document(
    UUID id,
    UUID ownerId,
    String title,
    String originalFilename,
    String storageKey,
    String mediaType,
    FileType fileType,
    long sizeBytes,
    String checksum,
    String language,
    DocumentStatus status,
    long currentVersion,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}

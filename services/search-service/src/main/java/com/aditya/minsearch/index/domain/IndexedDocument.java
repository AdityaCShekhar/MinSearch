package com.aditya.minsearch.index.domain;

import java.util.UUID;

public record IndexedDocument(UUID documentId, long documentVersion, int documentLength, boolean deleted) {}

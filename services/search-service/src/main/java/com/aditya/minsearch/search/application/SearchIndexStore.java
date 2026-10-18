package com.aditya.minsearch.search.application;

import com.aditya.minsearch.index.application.TextToken;
import com.aditya.minsearch.search.domain.SearchDocument;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

public interface SearchIndexStore {
  /** Returns the committed revision, or empty if this version was already applied. */
  OptionalLong save(Mutation mutation);

  List<StoredMutation> load();

  record Mutation(
      UUID eventId,
      UUID documentId,
      long version,
      boolean deleted,
      SearchDocument document,
      List<TextToken> tokens) {
    public Mutation {
      tokens = List.copyOf(tokens);
    }
  }

  record StoredMutation(long revision, Mutation mutation) {}
}

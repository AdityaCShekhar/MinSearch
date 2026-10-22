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

  default void forEach(java.util.function.Consumer<StoredMutation> consumer) {
    load().forEach(consumer);
  }

  record Mutation(
      UUID eventId,
      UUID documentId,
      long version,
      boolean deleted,
      SearchDocument document,
      List<TextToken> tokens, String text) {
    public Mutation(UUID eventId, UUID documentId, long version, boolean deleted, SearchDocument document, List<TextToken> tokens) {
      this(eventId,documentId,version,deleted,document,tokens,null);
    }
    public Mutation {
      tokens = List.copyOf(tokens);
    }
  }

  record StoredMutation(long revision, Mutation mutation) {}
}

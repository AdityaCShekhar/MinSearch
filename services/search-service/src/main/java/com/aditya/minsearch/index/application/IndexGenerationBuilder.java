package com.aditya.minsearch.index.application;

import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.index.domain.IndexedDocument;
import com.aditya.minsearch.index.domain.Posting;
import com.aditya.minsearch.index.domain.PostingList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IndexGenerationBuilder {
  private final Map<String, PostingList> source;
  private final Map<String, List<Posting>> postings = new HashMap<>();
  private final Map<UUID, IndexedDocument> documents = new HashMap<>();

  public IndexGenerationBuilder(IndexGeneration source) {
    this.source = source.postings();
    documents.putAll(source.documents());
  }

  public IndexGenerationBuilder replace(UUID id, long version, List<TextToken> tokens) {
    // New documents cannot have old postings. Avoid scanning the whole index during recovery.
    if (documents.containsKey(id)) remove(id);
    Map<String, List<Integer>> positions = new HashMap<>();
    for (TextToken token : tokens)
      positions.computeIfAbsent(token.term(), ignored -> new ArrayList<>()).add(token.position());
    positions.forEach(
        (term, values) -> mutablePostings(term).add(new Posting(id, version, values)));
    documents.put(
        id,
        new IndexedDocument(
            id,
            version,
            new HashSet<>(tokens.stream().map(TextToken::position).toList()).size(),
            false));
    return this;
  }

  public IndexGenerationBuilder remove(UUID id) {
    IndexedDocument previous = documents.get(id);
    if (previous == null || previous.deleted()) {
      documents.remove(id);
      return this;
    }
    // Copy only lists containing the replaced document; all other lists remain immutable shares.
    source.forEach(
        (term, list) -> {
          if (!postings.containsKey(term) && list.forDocument(id) != null) mutablePostings(term);
        });
    postings.values().forEach(list -> list.removeIf(posting -> posting.documentId().equals(id)));
    documents.remove(id);
    return this;
  }

  public IndexGenerationBuilder delete(UUID id, long version) {
    remove(id);
    documents.put(id, new IndexedDocument(id, version, 0, true));
    return this;
  }

  public IndexGeneration build(long generation) {
    Map<String, PostingList> result = new HashMap<>(source);
    postings.forEach(
        (term, list) -> {
          if (list.isEmpty()) result.remove(term);
          else result.put(term, new PostingList(list));
        });
    return new IndexGeneration(generation, result, documents);
  }

  private List<Posting> mutablePostings(String term) {
    return postings.computeIfAbsent(
        term,
        key -> {
          PostingList original = source.get(key);
          return original == null ? new ArrayList<>() : new ArrayList<>(original.postings());
        });
  }
}

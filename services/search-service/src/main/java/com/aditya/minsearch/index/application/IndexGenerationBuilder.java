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
  private final Map<String, List<Posting>> postings = new HashMap<>();
  private final Map<UUID, IndexedDocument> documents = new HashMap<>();

  public IndexGenerationBuilder(IndexGeneration source) {
    source.postings().forEach((term, list) -> postings.put(term, new ArrayList<>(list.postings())));
    documents.putAll(source.documents());
  }

  public IndexGenerationBuilder replace(UUID id, long version, List<TextToken> tokens) {
    remove(id);
    Map<String, List<Integer>> positions = new HashMap<>();
    for (TextToken token : tokens) positions.computeIfAbsent(token.term(), ignored -> new ArrayList<>()).add(token.position());
    positions.forEach((term, values) -> postings.computeIfAbsent(term, ignored -> new ArrayList<>())
        .add(new Posting(id, version, values)));
    documents.put(id, new IndexedDocument(id, version, new HashSet<>(tokens.stream().map(TextToken::position).toList()).size(), false));
    return this;
  }

  public IndexGenerationBuilder remove(UUID id) {
    postings.values().forEach(list -> list.removeIf(posting -> posting.documentId().equals(id)));
    postings.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    documents.remove(id);
    return this;
  }

  public IndexGenerationBuilder delete(UUID id, long version) {
    remove(id);
    documents.put(id, new IndexedDocument(id, version, 0, true));
    return this;
  }

  public IndexGeneration build(long generation) {
    Map<String, PostingList> result = new HashMap<>();
    postings.forEach((term, list) -> result.put(term, new PostingList(list)));
    return new IndexGeneration(generation, result, documents);
  }
}

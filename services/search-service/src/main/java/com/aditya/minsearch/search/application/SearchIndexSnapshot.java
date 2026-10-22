package com.aditya.minsearch.search.application;

import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.index.domain.PostingList;
import com.aditya.minsearch.search.domain.SearchDocument;
import java.util.Map;
import java.util.UUID;

/** A query must use the index and authorization metadata from the same publication. */
public record SearchIndexSnapshot(IndexGeneration generation, Map<UUID, SearchDocument> documents) {
  public SearchIndexSnapshot {
    documents = Map.copyOf(documents);
  }

  public static SearchIndexSnapshot empty() {
    return new SearchIndexSnapshot(IndexGeneration.empty(), Map.of());
  }

  public IndexGeneration generationForOwner(UUID owner) {
    var visible =
        documents.entrySet().stream()
            .filter(entry -> owner==null || owner.equals(entry.getValue().ownerId()))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    var postings = new java.util.HashMap<String, PostingList>();
    generation
        .postings()
        .forEach(
            (term, list) -> {
              var selected =
                  list.postings().stream()
                      .filter(posting -> visible.contains(posting.documentId()))
                      .toList();
              if (!selected.isEmpty()) postings.put(term, new PostingList(selected));
            });
    var indexed = new java.util.HashMap<UUID, com.aditya.minsearch.index.domain.IndexedDocument>();
    generation
        .documents()
        .forEach(
            (id, document) -> {
              if (visible.contains(id)) indexed.put(id, document);
            });
    return new IndexGeneration(generation.generation(), postings, indexed);
  }
}

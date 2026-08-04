package com.aditya.minsearch.index.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PostingList {
  private static final Comparator<Posting> ORDER = Comparator.comparing(Posting::documentId);
  private final List<Posting> postings;

  public PostingList(List<Posting> postings) {
    ArrayList<Posting> sorted = new ArrayList<>(postings);
    sorted.sort(ORDER);
    for (int i = 1; i < sorted.size(); i++) {
      if (sorted.get(i).documentId().equals(sorted.get(i - 1).documentId())) {
        throw new IllegalArgumentException("Duplicate document in posting list");
      }
    }
    this.postings = List.copyOf(sorted);
  }

  public List<Posting> postings() { return postings; }

  public Posting forDocument(UUID id) {
    return postings.stream().filter(posting -> posting.documentId().equals(id)).findFirst().orElse(null);
  }
}

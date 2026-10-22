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
    if (id == null) return null;
    int low = 0, high = postings.size() - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      Posting posting = postings.get(middle);
      int compared = posting.documentId().compareTo(id);
      if (compared == 0) return posting;
      if (compared < 0) low = middle + 1;
      else high = middle - 1;
    }
    return null;
  }
}

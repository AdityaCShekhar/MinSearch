package com.aditya.minsearch.index.application;

import com.aditya.minsearch.index.domain.IndexGeneration;
import com.aditya.minsearch.index.domain.IndexedDocument;
import com.aditya.minsearch.index.domain.Posting;
import com.aditya.minsearch.index.domain.PostingList;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class IndexSnapshotStore {
  private static final int MAGIC = 0x4D534958;

  public void save(IndexGeneration generation, Path snapshot) throws IOException {
    Files.createDirectories(snapshot.toAbsolutePath().getParent());
    Path temporary = snapshot.resolveSibling(snapshot.getFileName() + ".tmp");
    try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temporary))) {
      output.writeInt(MAGIC);
      output.writeLong(generation.generation());
      output.writeInt(generation.documents().size());
      for (IndexedDocument document : generation.documents().values()) {
        output.writeLong(document.documentId().getMostSignificantBits());
        output.writeLong(document.documentId().getLeastSignificantBits());
        output.writeLong(document.documentVersion());
        output.writeInt(document.documentLength());
        output.writeBoolean(document.deleted());
      }
      output.writeInt(generation.postings().size());
      for (var entry : generation.postings().entrySet()) {
        output.writeUTF(entry.getKey());
        output.writeInt(entry.getValue().postings().size());
        for (Posting posting : entry.getValue().postings()) {
          output.writeLong(posting.documentId().getMostSignificantBits());
          output.writeLong(posting.documentId().getLeastSignificantBits());
          output.writeLong(posting.documentVersion());
          output.writeInt(posting.positions().size());
          for (int position : posting.positions()) output.writeInt(position);
        }
      }
    }
    byte[] bytes = Files.readAllBytes(temporary);
    Files.writeString(snapshot.resolveSibling(snapshot.getFileName() + ".sha256"), hex(sha256(bytes)));
    Files.move(temporary, snapshot, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  public IndexGeneration load(Path snapshot) throws IOException {
    byte[] bytes = Files.readAllBytes(snapshot);
    Path checksum = snapshot.resolveSibling(snapshot.getFileName() + ".sha256");
    if (!Files.exists(checksum) || !Files.readString(checksum).trim().equals(hex(sha256(bytes)))) {
      throw new IOException("Index snapshot checksum mismatch");
    }
    try (DataInputStream input = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
      if (input.readInt() != MAGIC) throw new IOException("Unsupported index snapshot");
      long generation = input.readLong();
      Map<UUID, IndexedDocument> documents = new HashMap<>();
      int documentCount = input.readInt();
      for (int i = 0; i < documentCount; i++) {
        UUID id = uuid(input);
        documents.put(id, new IndexedDocument(id, input.readLong(), input.readInt(), input.readBoolean()));
      }
      Map<String, PostingList> postings = new HashMap<>();
      int termCount = input.readInt();
      for (int i = 0; i < termCount; i++) {
        String term = input.readUTF();
        List<Posting> values = new ArrayList<>();
        int postingCount = input.readInt();
        for (int j = 0; j < postingCount; j++) {
          UUID id = uuid(input);
          long version = input.readLong();
          int positions = input.readInt();
          List<Integer> valuesAt = new ArrayList<>();
          for (int k = 0; k < positions; k++) valuesAt.add(input.readInt());
          values.add(new Posting(id, version, valuesAt));
        }
        postings.put(term, new PostingList(values));
      }
      return new IndexGeneration(generation, postings, documents);
    }
  }

  private static UUID uuid(DataInputStream input) throws IOException {
    return new UUID(input.readLong(), input.readLong());
  }

  private static byte[] sha256(byte[] bytes) throws IOException {
    try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    catch (java.security.NoSuchAlgorithmException exception) { throw new IOException(exception); }
  }

  private static String hex(byte[] bytes) {
    return java.util.HexFormat.of().formatHex(bytes);
  }
}

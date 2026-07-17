package com.aditya.minsearch.document.domain.storage;

import java.io.IOException;
import java.io.InputStream;

public interface DocumentStorage {

  String store(String storageKey, InputStream content) throws IOException;

  InputStream open(String storageKey) throws IOException;

  void delete(String storageKey) throws IOException;
}

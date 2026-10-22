package com.aditya.minsearch.document.application;

import com.aditya.minsearch.document.domain.storage.DocumentStorage;
import org.springframework.transaction.support.*;

final class StoredFileCompensation {
  private StoredFileCompensation() {}
  static void onRollback(DocumentStorage storage, String key) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int status) {
        if (status==STATUS_ROLLED_BACK) {
          try { storage.delete(key); }
          catch (Exception failure) { org.slf4j.LoggerFactory.getLogger(StoredFileCompensation.class).error("Rollback file cleanup failed for opaque key {}", key); }
        }
      }
    });
  }
}

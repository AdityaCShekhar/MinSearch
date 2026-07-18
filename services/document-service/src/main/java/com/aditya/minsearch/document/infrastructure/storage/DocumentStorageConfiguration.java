package com.aditya.minsearch.document.infrastructure.storage;

import com.aditya.minsearch.document.domain.storage.DocumentStorage;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentStorageConfiguration {
  @Bean
  DocumentStorage documentStorage(
      @Value("${minsearch.storage.documents-root}") String documentsRoot) {
    return new LocalFilesystemDocumentStorage(Path.of(documentsRoot));
  }
}

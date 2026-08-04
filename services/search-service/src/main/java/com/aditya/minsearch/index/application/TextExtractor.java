package com.aditya.minsearch.index.application;

import com.aditya.minsearch.index.domain.SupportedFileType;
import java.io.IOException;
import java.io.InputStream;

public interface TextExtractor {
  String extract(InputStream content, SupportedFileType fileType) throws IOException;
}

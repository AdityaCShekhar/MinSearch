package com.aditya.minsearch.index.application;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** An untrusted PDF is parsed in a disposable JVM with a heap and wall-clock budget. */
public final class PdfExtractionWorker {
  private static final java.util.concurrent.Semaphore WORKERS = new java.util.concurrent.Semaphore(2, true);
  private PdfExtractionWorker() {}
  static String extract(byte[] bytes, int characters, int pages) throws IOException {
    Path directory = Files.createTempDirectory("minsearch-pdf-");
    Path input = directory.resolve("input.pdf"), output = directory.resolve("output.txt");
    Process process = null;
    boolean acquired = false;
    try {
      acquired = WORKERS.tryAcquire(10, TimeUnit.SECONDS);
      if (!acquired) throw new TextExtractionException("PDF worker capacity unavailable");
      Files.write(input, bytes);
      String classpath = System.getProperty("java.class.path");
      var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
          "-Xms16m", "-Xmx" + Integer.getInteger("minsearch.pdf.worker-heap-mib", 128) + "m", "-XX:+ExitOnOutOfMemoryError",
          "-Dloader.main=" + PdfExtractionWorker.class.getName(), "-cp", classpath));
      boolean boot = false;
      if (!classpath.contains(File.pathSeparator) && classpath.endsWith(".jar")) {
        try (var jar = new java.util.jar.JarFile(classpath)) { boot = jar.getEntry("org/springframework/boot/loader/launch/PropertiesLauncher.class") != null; }
      }
      command.add(boot ? "org.springframework.boot.loader.launch.PropertiesLauncher" : PdfExtractionWorker.class.getName());
      command.addAll(List.of(input.toString(), output.toString(), Integer.toString(characters), Integer.toString(pages),
          Integer.toString(Integer.getInteger("minsearch.pdf.max-objects", 100000))));
      process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      if (!process.waitFor(Integer.getInteger("minsearch.pdf.timeout-seconds", 10), TimeUnit.SECONDS)) {
        throw new TextExtractionException("PDF extraction time limit exceeded");
      }
      if (process.exitValue() != 0) {
        String failure = Files.exists(directory.resolve("error.txt")) ? Files.readString(directory.resolve("error.txt")) : "PDF parsing or memory limit exceeded";
        throw new TextExtractionException(failure);
      }
      if (!Files.exists(output) || Files.size(output) > (long) characters * 4 + 4) throw new TextExtractionException("Extracted text limit exceeded");
      return Files.readString(output, StandardCharsets.UTF_8);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("PDF extraction interrupted", interrupted);
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
        try { process.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      }
      try {
        for (String name : List.of("input.pdf", "output.txt", "error.txt")) Files.deleteIfExists(directory.resolve(name));
        Files.deleteIfExists(directory);
      } finally {
        if (acquired) WORKERS.release();
      }
    }
  }

  public static void main(String[] args) throws Exception {
    Path input = Path.of(args[0]), output = Path.of(args[1]);
    int maximumCharacters = Integer.parseInt(args[2]), maximumPages = Integer.parseInt(args[3]), maximumObjects = Integer.parseInt(args[4]);
    String safeFailure = "Document content could not be extracted";
    try (var document = Loader.loadPDF(input.toFile())) {
      if (document.isEncrypted()) { safeFailure = "Encrypted PDFs are not supported"; throw new IOException(); }
      if (document.getDocument().getXrefTable().size() > maximumObjects) { safeFailure = "PDF object limit exceeded"; throw new IOException(); }
      if (document.getNumberOfPages() > maximumPages) { safeFailure = "PDF page limit exceeded"; throw new IOException(); }
      try (Writer file = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
        var writer = new Writer() {
          int count;
          public void write(char[] buffer, int offset, int length) throws IOException {
            if (length > maximumCharacters - count) throw new TextExtractionException("Extracted text limit exceeded");
            count += length; file.write(buffer, offset, length);
          }
          public void flush() throws IOException { file.flush(); }
          public void close() throws IOException { file.close(); }
        };
        var stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.writeText(document, writer);
      }
    } catch (Throwable failure) {
      if (failure instanceof TextExtractionException) safeFailure = "Extracted text limit exceeded";
      if (failure instanceof org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException) safeFailure = "Encrypted PDFs are not supported";
      Files.writeString(input.resolveSibling("error.txt"), safeFailure, StandardCharsets.UTF_8);
      System.exit(1);
    }
  }
}

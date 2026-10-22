package com.aditya.minsearch.e2e;

import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Runs the actual packaged applications. Explicit -Pe2e never silently skips missing Docker. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformIT {
  @TempDir static Path documents;
  final ObjectMapper mapper=JsonMapper.builder().build();
  final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  final Network network=Network.newNetwork();
  final List<GenericContainer<?>> applications=new ArrayList<>();
  final PostgreSQLContainer authPostgres=database("auth");
  final PostgreSQLContainer documentPostgres=database("document");
  final PostgreSQLContainer searchPostgres=database("search");

  PostgreSQLContainer database(String owner) {
    return new PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName("minsearch_"+owner).withUsername("minsearch_"+owner)
        .withPassword(UUID.randomUUID().toString()).withNetwork(network).withNetworkAliases(owner+"-postgres");
  }
  final GenericContainer<?> redis=new GenericContainer<>("redis:7-alpine").withNetwork(network).withNetworkAliases("redis")
      .withCommand("redis-server", "--requirepass", "integration-redis-password-32-bytes").withExposedPorts(6379);
  final GenericContainer<?> kafka=new GenericContainer<>("apache/kafka:4.1.0").withNetwork(network).withNetworkAliases("kafka")
      .withEnv(Map.ofEntries(
          Map.entry("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk"), Map.entry("KAFKA_NODE_ID", "1"),
          Map.entry("KAFKA_PROCESS_ROLES", "broker,controller"),
          Map.entry("KAFKA_LISTENERS", "INTERNAL://:29092,CONTROLLER://:29093"),
          Map.entry("KAFKA_ADVERTISED_LISTENERS", "INTERNAL://kafka:29092"),
          Map.entry("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT"),
          Map.entry("KAFKA_INTER_BROKER_LISTENER_NAME", "INTERNAL"), Map.entry("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER"),
          Map.entry("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@kafka:29093"),
          Map.entry("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1"), Map.entry("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1"),
          Map.entry("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1"), Map.entry("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0"),
          Map.entry("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false"), Map.entry("KAFKA_HEAP_OPTS", "-Xms128m -Xmx384m")))
      .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1)).withStartupTimeout(Duration.ofMinutes(2));
  GenericContainer<?> auth, document, search;
  String signingKey=UUID.randomUUID()+"-"+UUID.randomUUID();
  String owner, outsider, admin, ownerId;
  Path root=Path.of("..").toAbsolutePath().normalize();

  @BeforeAll void startPlatform() throws Exception {
    authPostgres.start(); documentPostgres.start(); searchPostgres.start(); redis.start(); kafka.start();
    for (String topic : List.of("document.uploaded", "document.updated", "document.deleted", "index.completed", "index.failed")) {
      for (String name : List.of(topic, topic+".DLT")) {
        var created=kafka.execInContainer("/opt/kafka/bin/kafka-topics.sh", "--bootstrap-server", "kafka:29092", "--create", "--if-not-exists", "--topic", name, "--partitions", "3", "--replication-factor", "1");
        assertThat(created.getExitCode()).as("create topic %s", name).isZero();
      }
    }
    auth=application("auth-service", "auth-service-0.0.1-SNAPSHOT.jar", false);
    auth.start();
    document=application("document-service", "document-service-0.0.1-SNAPSHOT.jar", false);
    search=application("search-service", "search-service-0.0.1-SNAPSHOT-exec.jar", true);
    document.start(); search.start();
    var user=register("owner"); ownerId=user.id(); owner=user.token();
    outsider=register("outsider").token();
    var operator=register("admin");
    sql(authPostgres, "UPDATE users SET role='ADMIN' WHERE id='"+UUID.fromString(operator.id())+"'");
    admin=login(operator.email(), operator.password()).get("accessToken").asText();
  }

  GenericContainer<?> application(String service, String jar, boolean cache) {
    PostgreSQLContainer database=switch (service) {
      case "auth-service" -> authPostgres;
      case "document-service" -> documentPostgres;
      case "search-service" -> searchPostgres;
      default -> throw new IllegalArgumentException("Unknown service: "+service);
    };
    String databaseHost=service.replace("-service", "-postgres");
    var app=new GenericContainer<>(System.getProperty("minsearch.e2e.java-image", "eclipse-temurin:17-jre"))
        .withNetwork(network).withNetworkAliases(service).withExposedPorts(8080)
        .withCopyFileToContainer(MountableFile.forHostPath(root.resolve("services/"+service+"/target/"+jar)), "/app/app.jar")
        .withFileSystemBind(documents.toString(), "/data/documents", cache ? BindMode.READ_ONLY : BindMode.READ_WRITE)
        .withEnv("JAVA_TOOL_OPTIONS", "-Xms64m -Xmx384m -XX:+ExitOnOutOfMemoryError")
        .withEnv("MINSEARCH_DATABASE_URL", "jdbc:postgresql://"+databaseHost+":5432/"+database.getDatabaseName())
        .withEnv("MINSEARCH_DATABASE_USERNAME", database.getUsername()).withEnv("MINSEARCH_DATABASE_PASSWORD", database.getPassword())
        .withEnv("MINSEARCH_SECURITY_JWT_SECRET", signingKey).withEnv("MINSEARCH_DEPLOYMENT_MODE", "production")
        .withEnv("MINSEARCH_SECURITY_CORS_ALLOWED_ORIGINS", "https://client.example")
        .withEnv("MINSEARCH_STORAGE_DOCUMENTS_ROOT", "/data/documents")
        .withEnv("MINSEARCH_MESSAGING_ENABLED", "true").withEnv("MINSEARCH_KAFKA_BOOTSTRAP_SERVERS", "kafka:29092")
        .withEnv("MINSEARCH_CACHE_REDIS_ENABLED", Boolean.toString(cache)).withEnv("SPRING_DATA_REDIS_HOST", "redis")
        .withEnv("SPRING_DATA_REDIS_PASSWORD", "integration-redis-password-32-bytes")
        .withEnv("SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT", "1000")
        .withEnv("SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT", "1000")
        .withEnv("LOGGING_STRUCTURED_FORMAT_CONSOLE", "ecs")
        .withCommand("java", "-jar", "/app/app.jar")
        .waitingFor(Wait.forHttp("/actuator/health/readiness").forPort(8080).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(2));
    applications.add(app); return app;
  }

  @AfterAll void stopPlatform() throws Exception {
    Path logs=Path.of("target", "e2e-logs"); Files.createDirectories(logs);
    for (var app : applications) {
      if (app.getContainerId()!=null) {
        Files.writeString(logs.resolve(app==auth ? "auth.log" : app==document ? "document.log" : "search.log"), app.getLogs());
        app.stop();
      }
    }
    kafka.stop(); redis.stop(); searchPostgres.stop(); documentPostgres.stop(); authPostgres.stop(); network.close();
  }

  @Test void lifecycleIsolationPdfFailuresCacheOutageAndRestartRecovery() throws Exception {
    assertThat(response(search, "POST", "/api/v1/search", null, Map.of("query", "alpha")).statusCode()).isEqualTo(401);
    assertThat(response(search, "GET", "/actuator/prometheus", owner, null).statusCode()).isEqualTo(403);
    var scrape=response(search, "GET", "/actuator/prometheus", admin, null);
    assertThat(scrape.statusCode()).isEqualTo(200); assertThat(scrape.body()).contains("minsearch_index_generation");
    var uploaded=upload("notes.txt", "text/plain", "alpha beta gamma".getBytes(StandardCharsets.UTF_8), owner);
    String id=uploaded.get("id").asText();
    assertThat(uploaded.get("storageKey").asText()).doesNotContain("notes.txt").endsWith(".bin");
    await(() -> status(id).equals("INDEXED"), "upload becomes indexed");
    assertThat(total("\"alpha beta\"", owner)).isOne();
    assertThat(total("alpha", outsider)).isZero();
    assertThat(response(document, "GET", "/api/v1/documents/"+id, outsider, null).statusCode()).isEqualTo(400);
    var first=search("alpha", owner); var second=search("alpha", owner);
    assertThat(first).isEqualTo(second);
    var keys=redis.execInContainer("redis-cli", "-a", "integration-redis-password-32-bytes", "--scan", "--pattern", "minsearch:cache:v1:*");
    assertThat(keys.getStdout()).contains("minsearch:cache:v1:");
    stop(redis);
    try { assertThat(total("alpha OR absent", owner)).isOne(); assertHealthy(search); }
    finally { start(redis); }
    restart(search); await(() -> total("alpha", owner)==1, "durable token recovery after restart");
    var metadata=multipart(document, "PUT", "/api/v1/documents/"+id, owner, "notes.txt", "text/plain", null, "Changed title", "fr");
    assertThat(metadata.statusCode()).isEqualTo(202);
    await(() -> total("alpha language:fr", owner)==1, "metadata update becomes searchable");
    String pdfId=upload("valid.pdf", "application/pdf", pdf("pdf unique content"), owner).get("id").asText();
    await(() -> status(pdfId).equals("INDEXED"), "packaged isolated PDF worker");
    assertThat(total("unique", owner)).isOne();
    String failed=upload("malformed.pdf", "application/pdf", "%PDF-1.7\nMalformed PDF".getBytes(StandardCharsets.UTF_8), owner).get("id").asText();
    await(() -> status(failed).equals("INDEX_FAILED"), "malformed PDF reaches terminal failure");
    assertThat(total("alpha", owner)).isOne();
    assertThat(response(document, "DELETE", "/api/v1/documents/"+id, owner, null).statusCode()).isEqualTo(202);
    await(() -> status(id).equals("DELETED"), "index deletion and physical cleanup");
    assertThat(total("alpha", owner)).isZero();
    assertThat(Files.exists(documents.resolve(uploaded.get("storageKey").asText()))).isFalse();
    assertThat(number(documentPostgres, "SELECT count(*) FROM document_security_audit WHERE action='document.deleted' AND resource_id='"+UUID.fromString(id)+"'")).isOne();
    restart(search); assertThat(total("alpha", owner)).isZero(); assertThat(total("unique", owner)).isOne();
    await(() -> number(searchPostgres, "SELECT count(*) FROM search_history")>0, "asynchronous history is persisted");
  }

  @Test void brokerOutageRetainsOutboxAndDatabaseOutageOnlyFailsReadiness() throws Exception {
    stop(kafka);
    String id;
    try {
      id=upload("pending.txt", "text/plain", "resilient queued token".getBytes(StandardCharsets.UTF_8), owner).get("id").asText();
      assertThat(number(documentPostgres, "SELECT count(*) FROM document_outbox WHERE published_at IS NULL")).isPositive();
      assertHealthy(search);
    } finally { start(kafka); }
    await(() -> status(id).equals("INDEXED"), "outbox resumes after broker recovery");
    assertThat(total("resilient", owner)).isOne();
    stop(searchPostgres);
    try {
      await(() -> response(search, "GET", "/actuator/health/readiness", null, null).statusCode()==503, "database outage removes readiness");
      assertThat(response(search, "GET", "/actuator/health/liveness", null, null).statusCode()).isEqualTo(200);
    } finally { start(searchPostgres); }
    await(() -> response(search, "GET", "/actuator/health/readiness", null, null).statusCode()==200, "database readiness recovers");
  }

  @Test void databasesContainOnlyOwnedTablesAndDocumentOutageLeavesSearchAvailable() throws Exception {
    for (var database : List.of(authPostgres, documentPostgres, searchPostgres)) {
      assertThat(number(database, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='users'"))
          .isEqualTo(database==authPostgres ? 1 : 0);
      assertThat(number(database, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='documents'"))
          .isEqualTo(database==documentPostgres ? 1 : 0);
      assertThat(number(database, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='search_index_documents'"))
          .isEqualTo(database==searchPostgres ? 1 : 0);
      assertThat(number(database, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='document_visibility'"))
          .isZero();
    }
    String id=upload("isolated.txt", "text/plain", "databaseisolationtoken".getBytes(StandardCharsets.UTF_8), owner).get("id").asText();
    await(() -> status(id).equals("INDEXED"), "isolated document becomes indexed");
    stop(documentPostgres);
    try {
      await(() -> response(document, "GET", "/actuator/health/readiness", null, null).statusCode()==503, "only document readiness fails");
      assertHealthy(auth); assertHealthy(search);
      assertThat(total("databaseisolationtoken", owner)).isOne();
      assertThat(response(search, "GET", "/api/v1/autocomplete?prefix=databaseisolation", owner, null).statusCode()).isEqualTo(200);
      assertThat(response(search, "GET", "/api/v1/suggest?term=databaseisolationtokn", owner, null).statusCode()).isEqualTo(200);
    } finally { start(documentPostgres); }
    await(() -> response(document, "GET", "/actuator/health/readiness", null, null).statusCode()==200, "document database readiness recovers");
  }

  record Account(String id, String email, String password, String token) {}
  Account register(String name) throws Exception {
    String email=name+UUID.randomUUID()+"@example.com", password="integration-"+UUID.randomUUID();
    var registered=json(response(auth, "POST", "/api/v1/auth/register", null, Map.of("email", email, "password", password)), 201);
    return new Account(registered.get("id").asText(), email, password, login(email,password).get("accessToken").asText());
  }
  JsonNode login(String email, String password) throws Exception { return json(response(auth,"POST","/api/v1/auth/login",null,Map.of("email",email,"password",password)),200); }
  JsonNode upload(String filename, String mediaType, byte[] content, String token) throws Exception {
    return json(multipart(document,"POST","/api/v1/documents",token,filename,mediaType,content,"Test title","en"),202);
  }
  HttpResponse<String> multipart(GenericContainer<?> app, String method, String path, String token, String filename, String mediaType, byte[] bytes, String title, String language) throws Exception {
    String boundary="minsearch-e2e-boundary";
    var body=new ByteArrayOutputStream();
    for (var field : Map.of("title",title,"language",language).entrySet()) body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
    if (bytes!=null) {
      body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: "+mediaType+"\r\n\r\n").getBytes(StandardCharsets.UTF_8)); body.write(bytes); body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    body.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
    return http.send(HttpRequest.newBuilder(uri(app,path)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token)
        .header("Content-Type","multipart/form-data; boundary="+boundary).method(method,HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> response(GenericContainer<?> app, String method, String path, String token, Object payload) {
    try {
      var request=HttpRequest.newBuilder(uri(app,path)).timeout(Duration.ofSeconds(10));
      if (token!=null) request.header("Authorization","Bearer "+token);
      if (payload!=null) request.header("Content-Type","application/json");
      return http.send(request.method(method,payload==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build(),HttpResponse.BodyHandlers.ofString());
    } catch (Exception failure) { throw new IllegalStateException("HTTP probe failed: "+method+" "+path, failure); }
  }
  int currentPort(GenericContainer<?> container, int port) {
    // Docker can reassign an ephemeral host port on restart; GenericContainer caches its first inspection.
    var inspect=DockerClientFactory.instance().client().inspectContainerCmd(container.getContainerId()).exec();
    return Integer.parseInt(inspect.getNetworkSettings().getPorts().getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(port))[0].getHostPortSpec());
  }
  URI uri(GenericContainer<?> app, String path) { return URI.create("http://"+app.getHost()+":"+currentPort(app,8080)+path); }
  JsonNode json(HttpResponse<String> response, int status) { assertThat(response.statusCode()).isEqualTo(status); return mapper.readTree(response.body()); }
  JsonNode search(String query, String token) { return json(response(search,"POST","/api/v1/search",token,Map.of("query",query)),200); }
  long total(String query, String token) { return search(query,token).get("total").asLong(); }
  String status(String id) { return json(response(document,"GET","/api/v1/documents/"+id+"/status",owner,null),200).get("status").asText(); }
  void assertHealthy(GenericContainer<?> app) { assertThat(response(app,"GET","/actuator/health/readiness",null,null).statusCode()).isEqualTo(200); }
  void await(BooleanSupplier check, String description) {
    System.out.println("E2E: " + description);
    long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos(); Throwable last=null;
    while (System.nanoTime()<deadline) {
      try { if (check.getAsBoolean()) return; } catch (RuntimeException | AssertionError failure) { last=failure; }
      try { Thread.sleep(200); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
    throw new AssertionError(description, last);
  }
  String jdbcUrl(PostgreSQLContainer database) { return "jdbc:postgresql://"+database.getHost()+":"+currentPort(database,5432)+"/"+database.getDatabaseName(); }
  void sql(PostgreSQLContainer database, String sql) throws Exception { try (var connection=DriverManager.getConnection(jdbcUrl(database),database.getUsername(),database.getPassword()); var statement=connection.createStatement()) { statement.execute(sql); } }
  long number(PostgreSQLContainer database, String sql) {
    try (var connection=DriverManager.getConnection(jdbcUrl(database),database.getUsername(),database.getPassword()); var statement=connection.createStatement(); var rows=statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
    catch (SQLException failure) { throw new IllegalStateException("Database probe failed",failure); }
  }
  void stop(GenericContainer<?> container) { DockerClientFactory.instance().client().stopContainerCmd(container.getContainerId()).withTimeout(30).exec(); }
  void start(GenericContainer<?> container) { DockerClientFactory.instance().client().startContainerCmd(container.getContainerId()).exec(); }
  void restart(GenericContainer<?> container) { DockerClientFactory.instance().client().restartContainerCmd(container.getContainerId()).withTimeout(90).exec(); await(() -> response(container,"GET","/actuator/health/readiness",null,null).statusCode()==200,"service restart readiness"); }
  byte[] pdf(String text) throws Exception {
    try (var doc=new PDDocument(); var output=new ByteArrayOutputStream()) {
      var page=new PDPage(); doc.addPage(page);
      try (var content=new PDPageContentStream(doc,page)) { content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12); content.newLineAtOffset(50,700); content.showText(text); content.endText(); }
      doc.save(output); return output.toByteArray();
    }
  }
}

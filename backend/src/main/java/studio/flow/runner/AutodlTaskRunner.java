package studio.flow.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.flow.config.FlowStudioProperties;
import studio.flow.model.EditTask;

@Component
@ConditionalOnProperty(
    prefix = "flowstudio",
    name = "mock-runner",
    havingValue = "false")
public class AutodlTaskRunner implements TaskRunner {
  private static final String INTERNAL_TOKEN_HEADER = "X-FlowStudio-Token";

  private final FlowStudioProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public AutodlTaskRunner(
      FlowStudioProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    this.objectMapper = objectMapper;
  }

  @Override
  public RunnerResult run(EditTask task) throws Exception {
    requireBaseUrl();

    String responseText = postMultipart(task);
    Map<String, Object> response =
        objectMapper.readValue(responseText, new TypeReference<>() {});

    boolean success = Boolean.TRUE.equals(response.get("success"));
    String message = String.valueOf(response.getOrDefault("message", ""));
    if (!success) {
      return new RunnerResult(
          false, null, message.isBlank() ? "AutoDL edit failed." : message);
    }

    Object resultPathValue = response.get("resultPath");
    if (resultPathValue == null || String.valueOf(resultPathValue).isBlank()) {
      return new RunnerResult(
          false, null, "AutoDL succeeded but did not return resultPath.");
    }

    Path output =
        task.getTaskDir().resolve("result.mp4").toAbsolutePath().normalize();
    copyResult(String.valueOf(resultPathValue), output);
    return new RunnerResult(
        true,
        output,
        message.isBlank() ? "AutoDL edit success." : message);
  }

  @Override
  public void deleteTaskData(String username, String taskId) throws Exception {
    requireBaseUrl();
    delete(
        "/internal/users/"
            + encode(username)
            + "/tasks/"
            + encode(taskId));
  }

  @Override
  public void deleteUserData(String username) throws Exception {
    requireBaseUrl();
    delete("/internal/users/" + encode(username));
  }

  private void delete(String path) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(normalizeBaseUrl() + path))
            .timeout(Duration.ofSeconds(30))
            .DELETE();
    addInternalToken(builder);

    HttpResponse<String> response =
        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException(
          "AutoDL cleanup failed with HTTP "
              + response.statusCode()
              + ": "
              + response.body());
    }
  }

  private String postMultipart(EditTask task)
      throws IOException, InterruptedException {
    String boundary =
        "FlowStudioBoundary" + UUID.randomUUID().toString().replace("-", "");
    byte[] body = multipartBody(boundary, task);

    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(normalizeBaseUrl() + "/edit"))
            .version(HttpClient.Version.HTTP_1_1)
            .timeout(Duration.ofSeconds(properties.getRunnerTimeoutSeconds()))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    addInternalToken(builder);

    HttpResponse<String> response =
        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException(
          "AutoDL HTTP " + response.statusCode() + ": " + response.body());
    }
    return response.body();
  }

  private byte[] multipartBody(String boundary, EditTask task)
      throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    writeField(output, boundary, "username", task.getUsername());
    writeField(output, boundary, "taskId", task.getTaskId());
    writeField(
        output, boundary, "sourcePrompt", nullToEmpty(task.getSourcePrompt()));
    writeField(output, boundary, "targetPrompt", task.getTargetPrompt());
    writeField(output, boundary, "targetWord", task.getTargetWord());
    writeFile(
        output,
        boundary,
        "video",
        task.getInputVideoPath(),
        contentTypeFor(task.getInputVideoPath(), "video/mp4"));
    writeFile(
        output,
        boundary,
        "mask",
        task.getMaskPath(),
        contentTypeFor(task.getMaskPath(), "image/png"));
    output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return output.toByteArray();
  }

  private void writeField(
      ByteArrayOutputStream output,
      String boundary,
      String name,
      String value)
      throws IOException {
    output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(
        ("Content-Disposition: form-data; name=\""
                + name
                + "\"\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    output.write(nullToEmpty(value).getBytes(StandardCharsets.UTF_8));
    output.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private void writeFile(
      ByteArrayOutputStream output,
      String boundary,
      String name,
      Path path,
      String contentType)
      throws IOException {
    output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(
        ("Content-Disposition: form-data; name=\""
                + name
                + "\"; filename=\""
                + path.getFileName()
                + "\"\r\n")
            .getBytes(StandardCharsets.UTF_8));
    output.write(
        ("Content-Type: " + contentType + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    Files.copy(path, output);
    output.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private void copyResult(String resultPath, Path output)
      throws IOException, InterruptedException {
    if (resultPath.startsWith("/")) {
      resultPath = normalizeBaseUrl() + resultPath;
    }

    Files.createDirectories(output.getParent());

    if (resultPath.startsWith("http://")
        || resultPath.startsWith("https://")) {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(resultPath))
              .timeout(Duration.ofSeconds(properties.getRunnerTimeoutSeconds()))
              .GET();
      addInternalToken(builder);

      HttpResponse<byte[]> response =
          httpClient.send(
              builder.build(), HttpResponse.BodyHandlers.ofByteArray());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IOException(
            "AutoDL result download failed with HTTP "
                + response.statusCode());
      }
      if (response.body() == null || response.body().length == 0) {
        throw new IOException(
            "AutoDL result download returned empty content.");
      }

      Files.write(output, response.body());
      return;
    }

    Path source = Path.of(resultPath);
    if (!Files.exists(source)) {
      throw new IOException(
          "AutoDL result path is not accessible from Java backend: "
              + resultPath);
    }
    Files.copy(
        source, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }

  private void addInternalToken(HttpRequest.Builder builder) {
    String token = properties.getAutodlInternalToken();
    if (token != null && !token.isBlank()) {
      builder.header(INTERNAL_TOKEN_HEADER, token);
    }
  }

  private String contentTypeFor(Path path, String fallback)
      throws IOException {
    String detected = Files.probeContentType(path);
    return detected == null || detected.isBlank() ? fallback : detected;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private void requireBaseUrl() {
    if (properties.getAutodlBaseUrl() == null
        || properties.getAutodlBaseUrl().isBlank()) {
      throw new IllegalStateException(
          "AUTODL_BASE_URL is required when mock runner is disabled.");
    }
  }

  private String normalizeBaseUrl() {
    String baseUrl = properties.getAutodlBaseUrl().trim();
    return baseUrl.endsWith("/")
        ? baseUrl.substring(0, baseUrl.length() - 1)
        : baseUrl;
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}

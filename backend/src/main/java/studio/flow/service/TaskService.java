package studio.flow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import studio.flow.config.FlowStudioProperties;
import studio.flow.model.EditTask;
import studio.flow.model.TaskStatus;
import studio.flow.runner.RunnerResult;
import studio.flow.runner.TaskRunner;

@Service
public class TaskService {
  private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "webm");
  private static final Set<String> MASK_EXTENSIONS = Set.of("png", "jpg", "jpeg");
  private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,32}$");
  private static final Pattern TASK_ID_PATTERN =
      Pattern.compile("^task-[a-f0-9-]{8,36}$");
  private static final String TASK_METADATA_FILE = "task.json";

  private final FlowStudioProperties properties;
  private final TaskRunner runner;
  private final ObjectMapper objectMapper;

  private final Map<String, EditTask> tasks = new ConcurrentHashMap<>();
  private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
  private final Set<String> deletingUsers = ConcurrentHashMap.newKeySet();
  private final Set<String> deletingTasks = ConcurrentHashMap.newKeySet();

  private final AtomicInteger workerNumber = new AtomicInteger();
  private final ExecutorService taskExecutor =
      Executors.newCachedThreadPool(
          runnable -> {
            Thread thread =
                new Thread(runnable, "flowstudio-task-" + workerNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
          });

  public TaskService(
      FlowStudioProperties properties, TaskRunner runner, ObjectMapper objectMapper) {
    this.properties = properties;
    this.runner = runner;
    this.objectMapper = objectMapper;
  }

  public record TaskPage(
      List<EditTask> items,
      int page,
      int pageSize,
      long totalItems,
      int totalPages) {}

  @PostConstruct
  public void loadPersistedTasks() throws IOException {
    Path root = datasetRoot();
    Files.createDirectories(root);
    System.out.println("[FlowStudio] Dataset directory: " + root);

    try (Stream<Path> stream = Files.walk(root, 4)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> TASK_METADATA_FILE.equals(path.getFileName().toString()))
          .forEach(this::loadTaskSafely);
    }

    System.out.println("[FlowStudio] Restored " + tasks.size() + " persisted task(s).");
  }

  public EditTask createEditTask(
      String rawUsername,
      String projectName,
      String sourcePrompt,
      String targetPrompt,
      String targetWord,
      MultipartFile video,
      MultipartFile mask)
      throws IOException {
    String username = normalizeUsername(rawUsername);
    validateText(targetPrompt, "targetPrompt is required.");
    validateText(targetWord, "targetWord is required.");
    validateFile(video, VIDEO_EXTENSIONS, "video");
    validateFile(mask, MASK_EXTENSIONS, "mask");

    if (deletingUsers.contains(username)) {
      throw new IllegalStateException("This account is being deleted.");
    }

    String taskId = newTaskId();
    Path taskDir = userTasksRoot(username).resolve(taskId).toAbsolutePath().normalize();
    ensureInside(taskDir, userTasksRoot(username), "Invalid task directory.");
    Files.createDirectories(taskDir);

    try {
      EditTask task = new EditTask(taskId);
      task.setUsername(username);
      task.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
      task.setProjectName(defaultProjectName(projectName));
      task.setSourcePrompt(nullToEmpty(sourcePrompt));
      task.setTargetPrompt(targetPrompt.trim());
      task.setTargetWord(targetWord.trim());
      task.setTaskDir(taskDir);
      task.setInputVideoPath(saveMultipart(video, taskDir, "input", VIDEO_EXTENSIONS));
      task.setMaskPath(saveMultipart(mask, taskDir, "mask", MASK_EXTENSIONS));
      task.setMessage("Task created and waiting for runner.");

      savePromptMetadata(task);
      persistTask(task);

      String key = taskKey(username, taskId);
      tasks.put(key, task);
      submitTask(key);
      return task;
    } catch (IOException | RuntimeException error) {
      deleteDirectoryQuietly(taskDir);
      throw error;
    }
  }

  public Optional<EditTask> findForUser(String rawUsername, String taskId) {
    String username = normalizeUsername(rawUsername);
    if (!isValidTaskId(taskId) || deletingUsers.contains(username)) {
      return Optional.empty();
    }

    String key = taskKey(username, taskId);
    if (deletingTasks.contains(key)) {
      return Optional.empty();
    }

    EditTask task = tasks.get(key);
    if (task == null || !username.equals(task.getUsername())) {
      return Optional.empty();
    }
    return Optional.of(task);
  }

  public TaskPage listForUser(String rawUsername, int requestedPage, int pageSize) {
    String username = normalizeUsername(rawUsername);
    int safeSize = Math.max(1, Math.min(50, pageSize));

    List<EditTask> userTasks =
        tasks.values().stream()
            .filter(task -> username.equals(task.getUsername()))
            .filter(task -> !deletingTasks.contains(taskKey(username, task.getTaskId())))
            .sorted(
                Comparator.comparing(
                        (EditTask task) -> nullToEmpty(task.getCreatedAt()))
                    .reversed())
            .toList();

    long totalItems = userTasks.size();
    int totalPages = totalItems == 0 ? 0 : (int) Math.ceil(totalItems / (double) safeSize);
    int safePage =
        totalPages == 0 ? 0 : Math.max(0, Math.min(requestedPage, totalPages - 1));
    int from = Math.min(safePage * safeSize, userTasks.size());
    int to = Math.min(from + safeSize, userTasks.size());

    return new TaskPage(
        userTasks.subList(from, to), safePage, safeSize, totalItems, totalPages);
  }

  public long countForUser(String rawUsername) {
    String username = normalizeUsername(rawUsername);
    return tasks.values().stream()
        .filter(task -> username.equals(task.getUsername()))
        .filter(task -> !deletingTasks.contains(taskKey(username, task.getTaskId())))
        .count();
  }

  public void deleteTaskForUser(String rawUsername, String taskId) throws Exception {
    String username = normalizeUsername(rawUsername);
    EditTask task =
        findForUser(username, taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found."));

    String key = taskKey(username, taskId);
    deletingTasks.add(key);
    tasks.remove(key, task);

    Future<?> future = runningTasks.remove(key);
    if (future != null) {
      future.cancel(true);
    }

    Exception remoteError = null;
    try {
      runner.deleteTaskData(username, taskId);
    } catch (Exception error) {
      remoteError = error;
    }

    IOException localError = null;
    try {
      deleteRecursively(task.getTaskDir());
    } catch (IOException error) {
      localError = error;
    } finally {
      deletingTasks.remove(key);
    }

    if (remoteError != null) {
      throw new IOException(
          "The local history record was deleted, but AutoDL cleanup failed: "
              + safeMessage(remoteError),
          remoteError);
    }
    if (localError != null) {
      throw localError;
    }
  }

  public Path resolveTaskFile(String rawUsername, String taskId, String fileName) {
    EditTask task =
        findForUser(rawUsername, taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found."));

    if (fileName == null || fileName.isBlank() || Path.of(fileName).getNameCount() != 1) {
      throw new IllegalArgumentException("Invalid file name.");
    }

    Path taskDir = task.getTaskDir().toAbsolutePath().normalize();
    Path candidate = taskDir.resolve(fileName).toAbsolutePath().normalize();
    if (!candidate.startsWith(taskDir)
        || !Files.exists(candidate)
        || !Files.isRegularFile(candidate)) {
      throw new IllegalArgumentException("File not found.");
    }
    return candidate;
  }

  public void beginUserDeletion(String rawUsername) {
    String username = normalizeUsername(rawUsername);
    deletingUsers.add(username);

    tasks.forEach(
        (key, task) -> {
          if (!username.equals(task.getUsername())) {
            return;
          }
          deletingTasks.add(key);
          tasks.remove(key, task);
          Future<?> future = runningTasks.remove(key);
          if (future != null) {
            future.cancel(true);
          }
        });
  }

  public void activateUser(String rawUsername) {
    String username = normalizeUsername(rawUsername);
    deletingUsers.remove(username);

    Path taskRoot = userTasksRoot(username);
    if (!Files.exists(taskRoot)) {
      return;
    }

    try (Stream<Path> stream = Files.walk(taskRoot, 2)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> TASK_METADATA_FILE.equals(path.getFileName().toString()))
          .forEach(this::loadTaskSafely);
    } catch (IOException error) {
      System.err.println(
          "[FlowStudio] Could not reload tasks for @"
              + username
              + ": "
              + error.getMessage());
    }

    deletingTasks.removeIf(key -> key.startsWith(username + "/"));
  }

  private void submitTask(String key) {
    FutureTask<Void> worker =
        new FutureTask<>(
            () -> {
              try {
                runTask(key);
              } finally {
                runningTasks.remove(key);
              }
              return null;
            });

    runningTasks.put(key, worker);
    taskExecutor.execute(worker);
  }

  private void runTask(String key) {
    EditTask task = tasks.get(key);
    if (!isTaskCurrent(key, task)) {
      return;
    }

    try {
      synchronized (task) {
        if (!isTaskCurrent(key, task)) {
          return;
        }
        task.setStatus(TaskStatus.RUNNING);
        task.setMessage("Runner is processing the video.");
        task.setErrorMessage(null);
        persistTask(task);
      }

      RunnerResult result = runner.run(task);
      if (!isTaskCurrent(key, task)) {
        return;
      }

      synchronized (task) {
        if (!isTaskCurrent(key, task)) {
          return;
        }

        if (!result.success()) {
          failAndPersist(task, result.message());
          return;
        }

        Path resultPath = result.resultPath().toAbsolutePath().normalize();
        ensureInside(resultPath, task.getTaskDir(), "Runner returned an invalid result path.");
        if (!Files.isRegularFile(resultPath)) {
          throw new IOException("Runner reported success but result file is missing.");
        }

        task.setStatus(TaskStatus.SUCCESS);
        task.setResultVideoPath(resultPath);
        task.setResultUrl(
            "/api/files/" + task.getTaskId() + "/" + resultPath.getFileName());
        task.setMessage(
            result.message() == null || result.message().isBlank()
                ? "Edit completed."
                : result.message());
        task.setErrorMessage(null);
        persistTask(task);
      }
    } catch (Exception error) {
      if (error instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      if (isTaskCurrent(key, task)) {
        synchronized (task) {
          failAndPersist(task, error.getMessage());
        }
      }
    }
  }

  private boolean isTaskCurrent(String key, EditTask task) {
    return task != null
        && !deletingUsers.contains(task.getUsername())
        && !deletingTasks.contains(key)
        && tasks.get(key) == task;
  }

  private void failAndPersist(EditTask task, String message) {
    task.setStatus(TaskStatus.FAILED);
    task.setErrorMessage(
        message == null || message.isBlank() ? "Task failed." : message);
    task.setMessage("Task failed.");
    try {
      persistTask(task);
    } catch (IOException persistenceError) {
      System.err.println(
          "[FlowStudio] Could not persist failed task "
              + task.getTaskId()
              + ": "
              + persistenceError.getMessage());
    }
  }

  private void loadTaskSafely(Path metadataFile) {
    try {
      JsonNode node = objectMapper.readTree(metadataFile.toFile());
      String username = normalizeUsername(text(node, "username", ""));
      String taskId = text(node, "taskId", "");
      if (!isValidTaskId(taskId)) {
        throw new IllegalArgumentException("Invalid taskId in metadata.");
      }

      String key = taskKey(username, taskId);
      if (tasks.containsKey(key) || deletingUsers.contains(username)) {
        return;
      }

      Path taskDir = metadataFile.getParent().toAbsolutePath().normalize();
      ensureInside(taskDir, userTasksRoot(username), "Invalid task metadata path.");

      EditTask task = new EditTask(taskId);
      task.setUsername(username);
      task.setCreatedAt(text(node, "createdAt", ""));
      task.setProjectName(text(node, "projectName", ""));
      task.setSourcePrompt(text(node, "sourcePrompt", ""));
      task.setTargetPrompt(text(node, "targetPrompt", ""));
      task.setTargetWord(text(node, "targetWord", ""));
      task.setTaskDir(taskDir);
      task.setInputVideoPath(resolveStoredFile(taskDir, text(node, "inputVideo", "")));
      task.setMaskPath(resolveStoredFile(taskDir, text(node, "mask", "")));
      task.setResultVideoPath(resolveStoredFile(taskDir, text(node, "resultVideo", "")));
      task.setResultUrl(text(node, "resultUrl", ""));
      task.setMessage(text(node, "message", ""));
      task.setErrorMessage(blankToNull(text(node, "errorMessage", "")));

      TaskStatus restoredStatus = parseStatus(text(node, "status", "FAILED"));
      boolean changed = false;
      if (restoredStatus == TaskStatus.PENDING || restoredStatus == TaskStatus.RUNNING) {
        restoredStatus = TaskStatus.FAILED;
        task.setMessage("Task was interrupted by a backend restart.");
        task.setErrorMessage(
            "The previous render did not finish before the backend restarted.");
        changed = true;
      }

      if (restoredStatus == TaskStatus.SUCCESS
          && (task.getResultVideoPath() == null
              || !Files.isRegularFile(task.getResultVideoPath()))) {
        restoredStatus = TaskStatus.FAILED;
        task.setResultUrl("");
        task.setMessage("Stored result is missing.");
        task.setErrorMessage(
            "The task metadata exists, but the result file could not be found.");
        changed = true;
      }

      task.setStatus(restoredStatus);
      tasks.put(key, task);

      if (changed) {
        persistTask(task);
      }
    } catch (Exception error) {
      System.err.println(
          "[FlowStudio] Ignoring invalid task metadata "
              + metadataFile
              + ": "
              + error.getMessage());
    }
  }

  private void persistTask(EditTask task) throws IOException {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("taskId", task.getTaskId());
    metadata.put("username", task.getUsername());
    metadata.put("createdAt", task.getCreatedAt());
    metadata.put("projectName", task.getProjectName());
    metadata.put("sourcePrompt", task.getSourcePrompt());
    metadata.put("targetPrompt", task.getTargetPrompt());
    metadata.put("targetWord", task.getTargetWord());
    metadata.put("status", task.getStatus().name());
    metadata.put("inputVideo", fileName(task.getInputVideoPath()));
    metadata.put("mask", fileName(task.getMaskPath()));
    metadata.put("resultVideo", fileName(task.getResultVideoPath()));
    metadata.put("resultUrl", nullToEmpty(task.getResultUrl()));
    metadata.put("message", nullToEmpty(task.getMessage()));
    metadata.put("errorMessage", nullToEmpty(task.getErrorMessage()));

    Files.createDirectories(task.getTaskDir());
    Path target = task.getTaskDir().resolve(TASK_METADATA_FILE);
    Path temp = task.getTaskDir().resolve(TASK_METADATA_FILE + ".tmp");
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), metadata);
    moveReplacing(temp, target);
  }

  private void savePromptMetadata(EditTask task) throws IOException {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("taskId", task.getTaskId());
    metadata.put("username", task.getUsername());
    metadata.put("projectName", task.getProjectName());
    metadata.put("sourcePrompt", task.getSourcePrompt());
    metadata.put("targetPrompt", task.getTargetPrompt());
    metadata.put("targetWord", task.getTargetWord());
    metadata.put("inputVideo", task.getInputVideoPath().getFileName().toString());
    metadata.put("mask", task.getMaskPath().getFileName().toString());
    metadata.put("createdAt", task.getCreatedAt());

    Path target = task.getTaskDir().resolve("prompt.json");
    Path temp = task.getTaskDir().resolve("prompt.json.tmp");
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), metadata);
    moveReplacing(temp, target);
  }

  private Path saveMultipart(
      MultipartFile file, Path taskDir, String baseName, Set<String> extensions)
      throws IOException {
    String extension = extensionOf(file.getOriginalFilename());
    if (!extensions.contains(extension)) {
      throw new IllegalArgumentException("Unsupported file type.");
    }

    Path output = taskDir.resolve(baseName + "." + extension).toAbsolutePath().normalize();
    ensureInside(output, taskDir, "Invalid upload path.");
    file.transferTo(output);
    return output;
  }

  private void validateFile(MultipartFile file, Set<String> extensions, String label) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException(label + " file is required.");
    }
    String extension = extensionOf(file.getOriginalFilename());
    if (!extensions.contains(extension)) {
      throw new IllegalArgumentException(label + " file type is not supported.");
    }
  }

  private String normalizeUsername(String username) {
    String value =
        username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    if (!USERNAME_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid username.");
    }
    return value;
  }

  private Path datasetRoot() {
    return properties.getDatasetDir().toAbsolutePath().normalize();
  }

  private Path userTasksRoot(String username) {
    Path root = datasetRoot();
    Path tasksRoot =
        root.resolve(username).resolve("tasks").toAbsolutePath().normalize();
    ensureInside(tasksRoot, root, "Invalid user task path.");
    return tasksRoot;
  }

  private void ensureInside(Path candidate, Path root, String message) {
    Path normalizedCandidate = candidate.toAbsolutePath().normalize();
    Path normalizedRoot = root.toAbsolutePath().normalize();
    if (!normalizedCandidate.startsWith(normalizedRoot)) {
      throw new IllegalArgumentException(message);
    }
  }

  private String extensionOf(String originalName) {
    String cleanName = StringUtils.cleanPath(originalName == null ? "" : originalName);
    int dot = cleanName.lastIndexOf('.');
    return dot >= 0 ? cleanName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
  }

  private String defaultProjectName(String projectName) {
    if (projectName == null || projectName.isBlank()) {
      return "FlowStudio " + DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    }
    return projectName.trim();
  }

  private void validateText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }

  private boolean isValidTaskId(String taskId) {
    return taskId != null && TASK_ID_PATTERN.matcher(taskId).matches();
  }

  private String taskKey(String username, String taskId) {
    return username + "/" + taskId;
  }

  private String newTaskId() {
    return "task-" + UUID.randomUUID();
  }

  private String fileName(Path path) {
    return path == null ? "" : path.getFileName().toString();
  }

  private Path resolveStoredFile(Path taskDir, String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return null;
    }
    Path candidate = taskDir.resolve(fileName).toAbsolutePath().normalize();
    ensureInside(candidate, taskDir, "Invalid stored file path.");
    return candidate;
  }

  private TaskStatus parseStatus(String value) {
    try {
      return TaskStatus.valueOf(value);
    } catch (Exception ignored) {
      return TaskStatus.FAILED;
    }
  }

  private String text(JsonNode node, String field, String fallback) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? fallback : value.asText(fallback);
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String safeMessage(Exception error) {
    return error.getMessage() == null || error.getMessage().isBlank()
        ? error.getClass().getSimpleName()
        : error.getMessage();
  }

  private void moveReplacing(Path source, Path target) throws IOException {
    try {
      Files.move(
          source,
          target,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void deleteRecursively(Path root) throws IOException {
    if (root == null || !Files.exists(root)) {
      return;
    }
    try (Stream<Path> stream = Files.walk(root)) {
      for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(item);
      }
    }
  }

  private void deleteDirectoryQuietly(Path root) {
    try {
      deleteRecursively(root);
    } catch (IOException ignored) {
      // The original upload exception is more useful.
    }
  }

  @PreDestroy
  public void shutdown() {
    runningTasks.values().forEach(future -> future.cancel(true));
    runningTasks.clear();
    taskExecutor.shutdownNow();
  }
}

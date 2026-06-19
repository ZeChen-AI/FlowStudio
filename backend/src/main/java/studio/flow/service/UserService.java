package studio.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import studio.flow.config.FlowStudioProperties;
import studio.flow.model.UserRecord;
import studio.flow.runner.TaskRunner;

@Service
public class UserService {
  private static final Pattern USERNAME_PATTERN =
      Pattern.compile("^[a-z0-9_-]{3,32}$");

  private final FlowStudioProperties properties;
  private final UserStore userStore;
  private final PasswordHasher passwordHasher;
  private final TaskService taskService;
  private final TaskRunner taskRunner;
  private final ObjectMapper objectMapper;

  public UserService(
      FlowStudioProperties properties,
      UserStore userStore,
      PasswordHasher passwordHasher,
      TaskService taskService,
      TaskRunner taskRunner,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.userStore = userStore;
    this.passwordHasher = passwordHasher;
    this.taskService = taskService;
    this.taskRunner = taskRunner;
    this.objectMapper = objectMapper;
  }

  public String register(String rawUsername, String rawPassword) throws Exception {
    String username = normalizeUsername(rawUsername);
    validatePassword(rawPassword);

    if (userStore.findByUsername(username).isPresent()) {
      throw new IllegalArgumentException("Username already exists.");
    }

    String createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    UserRecord user =
        new UserRecord(username, passwordHasher.hash(rawPassword), createdAt);

    Path root = userRoot(username);
    Files.createDirectories(root.resolve("tasks"));
    userStore.save(user);

    try {
      writeUserMetadata(user);
      taskService.activateUser(username);
      return username;
    } catch (Exception error) {
      userStore.delete(username);
      deleteRecursively(root);
      throw error;
    }
  }

  public String login(String rawUsername, String rawPassword) throws Exception {
    String username = normalizeUsername(rawUsername);
    UserRecord user =
        userStore
            .findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("用户名或者密码错误"));

    if (!passwordHasher.verify(rawPassword, user.getPasswordHash())) {
      throw new IllegalArgumentException("用户名或者密码错误");
    }

    Files.createDirectories(userRoot(username).resolve("tasks"));
    writeUserMetadata(user);
    taskService.activateUser(username);
    return username;
  }

  public Optional<UserRecord> findUser(String rawUsername) throws Exception {
    return userStore.findByUsername(normalizeUsername(rawUsername));
  }

  public boolean accountMatches(String rawUsername, String createdAt) throws Exception {
    if (createdAt == null || createdAt.isBlank()) {
      return false;
    }
    return findUser(rawUsername)
        .map(user -> createdAt.equals(user.getCreatedAt()))
        .orElse(false);
  }

  public void deleteAccount(String rawUsername) throws Exception {
    String username = normalizeUsername(rawUsername);
    taskService.beginUserDeletion(username);

    boolean completed = false;
    try {
      taskRunner.deleteUserData(username);
      deleteRecursively(userRoot(username));
      userStore.delete(username);
      completed = true;
    } finally {
      if (!completed) {
        taskService.activateUser(username);
      }
    }
  }

  public Path userRoot(String username) {
    String normalized = normalizeUsername(username);
    Path datasetRoot = properties.getDatasetDir().toAbsolutePath().normalize();
    Path root = datasetRoot.resolve(normalized).toAbsolutePath().normalize();
    if (!root.startsWith(datasetRoot)) {
      throw new IllegalArgumentException("Invalid username path.");
    }
    return root;
  }

  private void writeUserMetadata(UserRecord user) throws IOException {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("username", user.getUsername());
    metadata.put("createdAt", user.getCreatedAt());

    Path root = userRoot(user.getUsername());
    Files.createDirectories(root);
    Path target = root.resolve("user.json");
    Path temp = root.resolve("user.json.tmp");
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), metadata);

    try {
      Files.move(
          temp,
          target,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String normalizeUsername(String username) {
    String value =
        username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    if (!USERNAME_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Username must be 3-32 chars: lowercase letters, numbers, _ or -.");
    }
    return value;
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 6 || password.length() > 72) {
      throw new IllegalArgumentException("Password must be 6-72 characters.");
    }
  }

  private void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }

    try (var stream = Files.walk(path)) {
      for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(item);
      }
    }
  }
}

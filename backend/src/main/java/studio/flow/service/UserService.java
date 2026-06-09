package studio.flow.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import studio.flow.config.FlowStudioProperties;
import studio.flow.model.UserRecord;

@Service
public class UserService {
  private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,32}$");

  private final FlowStudioProperties properties;
  private final UserStore userStore;
  private final PasswordHasher passwordHasher;

  public UserService(FlowStudioProperties properties, UserStore userStore, PasswordHasher passwordHasher) {
    this.properties = properties;
    this.userStore = userStore;
    this.passwordHasher = passwordHasher;
  }

  public String register(String rawUsername, String rawPassword) throws Exception {
    String username = normalizeUsername(rawUsername);
    validatePassword(rawPassword);

    if (userStore.findByUsername(username).isPresent()) {
      throw new IllegalArgumentException("Username already exists.");
    }

    String passwordHash = passwordHasher.hash(rawPassword);
    String createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    userStore.save(new UserRecord(username, passwordHash, createdAt));
    Files.createDirectories(userRoot(username));
    return username;
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

    Files.createDirectories(userRoot(username));
    return username;
  }

  public void deleteAccount(String username) throws Exception {
    userStore.delete(username);
    deleteRecursively(userRoot(username));
  }

  public Path userRoot(String username) {
    String normalized = normalizeUsername(username);
    Path datasetRoot = properties.getDatasetDir().toAbsolutePath().normalize();
    Path userRoot = datasetRoot.resolve(normalized).normalize();
    if (!userRoot.startsWith(datasetRoot)) {
      throw new IllegalArgumentException("Invalid username path.");
    }
    return userRoot;
  }

  private String normalizeUsername(String username) {
    String value = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    if (!USERNAME_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Username must be 3-32 chars: lowercase letters, numbers, _ or -.");
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

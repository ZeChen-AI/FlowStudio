package studio.flow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import studio.flow.config.FlowStudioProperties;
import studio.flow.dto.AdminUserPageResponse;
import studio.flow.dto.AdminUserResponse;
import studio.flow.model.UserRecord;

@Service
public class AdminService {
  private static final int PAGE_SIZE = 5;
  private static final String PASSWORD_DISPLAY = "•••••••• (protected)";

  private final FlowStudioProperties properties;
  private final UserStore userStore;
  private final TaskService taskService;
  private final UserService userService;

  public AdminService(
      FlowStudioProperties properties,
      UserStore userStore,
      TaskService taskService,
      UserService userService) {
    this.properties = properties;
    this.userStore = userStore;
    this.taskService = taskService;
    this.userService = userService;
  }

  public boolean authenticate(String username, String password) {
    String expectedUsername = nullToEmpty(properties.getAdmin().getUsername());
    String expectedPassword = nullToEmpty(properties.getAdmin().getPassword());

    return secureEquals(nullToEmpty(username).trim(), expectedUsername)
        && secureEquals(nullToEmpty(password), expectedPassword);
  }

  public boolean isConfiguredAdmin(String username) {
    return secureEquals(
        nullToEmpty(username), nullToEmpty(properties.getAdmin().getUsername()));
  }

  public String configuredUsername() {
    return nullToEmpty(properties.getAdmin().getUsername());
  }

  public AdminUserPageResponse listUsers(int requestedPage) throws Exception {
    List<UserRecord> users =
        userStore.findAll().stream()
            .sorted(
                Comparator.comparing(
                        (UserRecord user) -> nullToEmpty(user.getCreatedAt()))
                    .reversed())
            .toList();

    long totalItems = users.size();
    int totalPages =
        totalItems == 0 ? 0 : (int) Math.ceil(totalItems / (double) PAGE_SIZE);
    int safePage =
        totalPages == 0
            ? 0
            : Math.max(0, Math.min(requestedPage, totalPages - 1));
    int from = Math.min(safePage * PAGE_SIZE, users.size());
    int to = Math.min(from + PAGE_SIZE, users.size());

    List<AdminUserResponse> items =
        users.subList(from, to).stream()
            .map(
                user ->
                    new AdminUserResponse(
                        user.getUsername(),
                        PASSWORD_DISPLAY,
                        user.getCreatedAt(),
                        taskService.countForUser(user.getUsername())))
            .toList();

    return new AdminUserPageResponse(
        safePage, PAGE_SIZE, totalItems, totalPages, items);
  }

  public void deleteUser(String username) throws Exception {
    userService
        .findUser(username)
        .orElseThrow(() -> new IllegalArgumentException("User not found."));
    userService.deleteAccount(username);
  }

  private boolean secureEquals(String left, String right) {
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8),
        right.getBytes(StandardCharsets.UTF_8));
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}

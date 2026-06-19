package studio.flow.service;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.stereotype.Service;
import studio.flow.controller.UnauthorizedException;
import studio.flow.model.UserRecord;

@Service
public class SessionAuthService {
  public static final String SESSION_USER = "FLOWSTUDIO_USER";
  public static final String SESSION_USER_CREATED_AT = "FLOWSTUDIO_USER_CREATED_AT";

  private final UserService userService;

  public SessionAuthService(UserService userService) {
    this.userService = userService;
  }

  public void bind(HttpSession session, String username) throws Exception {
    UserRecord user =
        userService
            .findUser(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found."));
    session.setAttribute(SESSION_USER, user.getUsername());
    session.setAttribute(SESSION_USER_CREATED_AT, user.getCreatedAt());
  }

  public Optional<String> currentUsername(HttpSession session) {
    try {
      Object usernameValue = session.getAttribute(SESSION_USER);
      Object createdAtValue = session.getAttribute(SESSION_USER_CREATED_AT);
      if (usernameValue == null || createdAtValue == null) {
        return Optional.empty();
      }

      String username = String.valueOf(usernameValue);
      String createdAt = String.valueOf(createdAtValue);
      if (username.isBlank() || createdAt.isBlank()) {
        return Optional.empty();
      }

      if (!userService.accountMatches(username, createdAt)) {
        invalidateQuietly(session);
        return Optional.empty();
      }
      return Optional.of(username);
    } catch (Exception error) {
      invalidateQuietly(session);
      return Optional.empty();
    }
  }

  public String requireUsername(HttpSession session) {
    return currentUsername(session)
        .orElseThrow(() -> new UnauthorizedException("Please login first."));
  }

  private void invalidateQuietly(HttpSession session) {
    try {
      session.invalidate();
    } catch (IllegalStateException ignored) {
      // The session was already invalidated.
    }
  }
}

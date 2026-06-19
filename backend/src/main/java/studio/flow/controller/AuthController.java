package studio.flow.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.flow.dto.AuthRequest;
import studio.flow.dto.AuthResponse;
import studio.flow.service.SessionAuthService;
import studio.flow.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  public static final String SESSION_USER = SessionAuthService.SESSION_USER;

  private final UserService userService;
  private final SessionAuthService sessionAuthService;

  public AuthController(
      UserService userService, SessionAuthService sessionAuthService) {
    this.userService = userService;
    this.sessionAuthService = sessionAuthService;
  }

  @GetMapping("/me")
  public AuthResponse me(HttpSession session) {
    return sessionAuthService
        .currentUsername(session)
        .map(username -> new AuthResponse(true, username, "Logged in."))
        .orElseGet(() -> new AuthResponse(false, "", "Not logged in."));
  }

  @PostMapping("/register")
  public AuthResponse register(
      @RequestBody AuthRequest request, HttpSession session) throws Exception {
    String username = userService.register(request.username(), request.password());
    sessionAuthService.bind(session, username);
    return new AuthResponse(true, username, "Register success.");
  }

  @PostMapping("/login")
  public AuthResponse login(
      @RequestBody AuthRequest request, HttpSession session) throws Exception {
    String username = userService.login(request.username(), request.password());
    sessionAuthService.bind(session, username);
    return new AuthResponse(true, username, "Login success.");
  }

  @PostMapping("/logout")
  public AuthResponse logout(HttpSession session) {
    try {
      session.invalidate();
    } catch (IllegalStateException ignored) {
      // Session already expired.
    }
    return new AuthResponse(false, "", "Logged out.");
  }

  @PostMapping("/delete")
  public AuthResponse deleteAccount(HttpSession session) throws Exception {
    String username = sessionAuthService.requireUsername(session);
    userService.deleteAccount(username);
    try {
      session.invalidate();
    } catch (IllegalStateException ignored) {
      // Session already expired.
    }
    return new AuthResponse(false, "", "Account deleted.");
  }
}

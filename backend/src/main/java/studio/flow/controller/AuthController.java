package studio.flow.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.flow.dto.AuthRequest;
import studio.flow.dto.AuthResponse;
import studio.flow.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  public static final String SESSION_USER = "FLOWSTUDIO_USER";

  private final UserService userService;

  public AuthController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public AuthResponse me(HttpSession session) {
    String username = sessionUsername(session);
    if (username == null) {
      return new AuthResponse(false, "", "Not logged in.");
    }
    return new AuthResponse(true, username, "Logged in.");
  }

  @PostMapping("/register")
  public AuthResponse register(@RequestBody AuthRequest request, HttpSession session) throws Exception {
    String username = userService.register(request.username(), request.password());
    session.setAttribute(SESSION_USER, username);
    return new AuthResponse(true, username, "Register success.");
  }

  @PostMapping("/login")
  public AuthResponse login(@RequestBody AuthRequest request, HttpSession session) throws Exception {
    String username = userService.login(request.username(), request.password());
    session.setAttribute(SESSION_USER, username);
    return new AuthResponse(true, username, "Login success.");
  }

  @PostMapping("/logout")
  public AuthResponse logout(HttpSession session) {
    session.invalidate();
    return new AuthResponse(false, "", "Logged out.");
  }

  @PostMapping("/delete")
  public AuthResponse deleteAccount(HttpSession session) throws Exception {
    String username = requireUsername(session);
    userService.deleteAccount(username);
    session.invalidate();
    return new AuthResponse(false, "", "Account deleted.");
  }

  public static String requireUsername(HttpSession session) {
    String username = sessionUsername(session);
    if (username == null) {
      throw new UnauthorizedException("Please login first.");
    }
    return username;
  }

  private static String sessionUsername(HttpSession session) {
    Object value = session.getAttribute(SESSION_USER);
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }
    return String.valueOf(value);
  }
}

package studio.flow.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.flow.dto.ActionResponse;
import studio.flow.dto.AdminAuthResponse;
import studio.flow.dto.AdminUserPageResponse;
import studio.flow.dto.AuthRequest;
import studio.flow.service.AdminService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private static final String SESSION_ADMIN = "FLOWSTUDIO_ADMIN";
  private static final String SESSION_ADMIN_USERNAME = "FLOWSTUDIO_ADMIN_USERNAME";

  private final AdminService adminService;

  public AdminController(AdminService adminService) {
    this.adminService = adminService;
  }

  @GetMapping("/me")
  public AdminAuthResponse me(HttpSession session) {
    String username = currentAdmin(session);
    return username == null
        ? new AdminAuthResponse(false, "", "Administrator login required.")
        : new AdminAuthResponse(true, username, "Administrator authenticated.");
  }

  @PostMapping("/login")
  public AdminAuthResponse login(
      @RequestBody AuthRequest request, HttpSession session) {
    if (!adminService.authenticate(request.username(), request.password())) {
      throw new UnauthorizedException("Administrator username or password is incorrect.");
    }

    String username = adminService.configuredUsername();
    session.setAttribute(SESSION_ADMIN, Boolean.TRUE);
    session.setAttribute(SESSION_ADMIN_USERNAME, username);
    return new AdminAuthResponse(true, username, "Administrator login success.");
  }

  @PostMapping("/logout")
  public AdminAuthResponse logout(HttpSession session) {
    session.removeAttribute(SESSION_ADMIN);
    session.removeAttribute(SESSION_ADMIN_USERNAME);
    return new AdminAuthResponse(false, "", "Administrator logged out.");
  }

  @GetMapping("/users")
  public AdminUserPageResponse users(
      @RequestParam(defaultValue = "0") int page, HttpSession session)
      throws Exception {
    requireAdmin(session);
    return adminService.listUsers(page);
  }

  @DeleteMapping("/users/{username}")
  public ActionResponse deleteUser(
      @PathVariable String username, HttpSession session) throws Exception {
    requireAdmin(session);
    adminService.deleteUser(username);
    return new ActionResponse(true, "User deleted.");
  }

  private void requireAdmin(HttpSession session) {
    if (currentAdmin(session) == null) {
      throw new UnauthorizedException("Administrator login required.");
    }
  }

  private String currentAdmin(HttpSession session) {
    Object authenticated = session.getAttribute(SESSION_ADMIN);
    Object usernameValue = session.getAttribute(SESSION_ADMIN_USERNAME);
    if (!Boolean.TRUE.equals(authenticated) || usernameValue == null) {
      return null;
    }

    String username = String.valueOf(usernameValue);
    return adminService.isConfiguredAdmin(username) ? username : null;
  }
}

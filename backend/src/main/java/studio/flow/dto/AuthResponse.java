package studio.flow.dto;

public record AuthResponse(boolean authenticated, String username, String message) {}

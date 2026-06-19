package studio.flow.dto;

public record AdminAuthResponse(
    boolean authenticated, String username, String message) {}

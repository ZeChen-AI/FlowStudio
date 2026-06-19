package studio.flow.dto;

public record AdminUserResponse(
    String username,
    String passwordDisplay,
    String createdAt,
    long interactionCount) {}

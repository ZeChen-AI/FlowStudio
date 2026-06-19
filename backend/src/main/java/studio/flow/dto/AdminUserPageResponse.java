package studio.flow.dto;

import java.util.List;

public record AdminUserPageResponse(
    int page,
    int pageSize,
    long totalItems,
    int totalPages,
    List<AdminUserResponse> items) {}

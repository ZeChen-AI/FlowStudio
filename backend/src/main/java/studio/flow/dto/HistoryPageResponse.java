package studio.flow.dto;

import java.util.List;

public record HistoryPageResponse(
    int page,
    int pageSize,
    long totalItems,
    int totalPages,
    List<HistoryTaskResponse> items) {}

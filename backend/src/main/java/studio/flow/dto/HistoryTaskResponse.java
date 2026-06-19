package studio.flow.dto;

public record HistoryTaskResponse(
    String taskId,
    String projectName,
    String status,
    String createdAt,
    String maskFileName,
    String maskUrl,
    String inputFileName,
    String inputUrl,
    String resultFileName,
    String resultUrl) {}

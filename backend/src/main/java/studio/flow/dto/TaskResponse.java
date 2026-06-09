package studio.flow.dto;

public record TaskResponse(
    String taskId,
    String projectName,
    String sourcePrompt,
    String targetPrompt,
    String targetWord,
    String status,
    String resultUrl,
    String message,
    String errorMessage) {}

package studio.flow.controller;

import jakarta.servlet.http.HttpSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import studio.flow.dto.ActionResponse;
import studio.flow.dto.HistoryPageResponse;
import studio.flow.dto.HistoryTaskResponse;
import studio.flow.model.EditTask;
import studio.flow.service.SessionAuthService;
import studio.flow.service.TaskService;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
  private static final int PAGE_SIZE = 5;

  private final TaskService taskService;
  private final SessionAuthService sessionAuthService;

  public HistoryController(
      TaskService taskService, SessionAuthService sessionAuthService) {
    this.taskService = taskService;
    this.sessionAuthService = sessionAuthService;
  }

  @GetMapping
  public HistoryPageResponse history(
      @RequestParam(defaultValue = "0") int page, HttpSession session) {
    String username = sessionAuthService.requireUsername(session);
    TaskService.TaskPage taskPage =
        taskService.listForUser(username, page, PAGE_SIZE);

    List<HistoryTaskResponse> items =
        taskPage.items().stream().map(this::toHistoryResponse).toList();

    return new HistoryPageResponse(
        taskPage.page(),
        taskPage.pageSize(),
        taskPage.totalItems(),
        taskPage.totalPages(),
        items);
  }

  @DeleteMapping("/{taskId}")
  public ActionResponse deleteHistory(
      @PathVariable String taskId, HttpSession session) throws Exception {
    String username = sessionAuthService.requireUsername(session);
    taskService.deleteTaskForUser(username, taskId);
    return new ActionResponse(true, "Interaction deleted.");
  }

  private HistoryTaskResponse toHistoryResponse(EditTask task) {
    String inputFile = fileName(task.getInputVideoPath());
    String maskFile = fileName(task.getMaskPath());
    String resultFile =
        task.getResultVideoPath() != null
                && Files.isRegularFile(task.getResultVideoPath())
            ? fileName(task.getResultVideoPath())
            : "";

    return new HistoryTaskResponse(
        task.getTaskId(),
        task.getProjectName(),
        task.getStatus().name(),
        task.getCreatedAt(),
        maskFile,
        fileUrl(task.getTaskId(), maskFile),
        inputFile,
        fileUrl(task.getTaskId(), inputFile),
        resultFile,
        fileUrl(task.getTaskId(), resultFile));
  }

  private String fileName(Path path) {
    return path == null ? "" : path.getFileName().toString();
  }

  private String fileUrl(String taskId, String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "";
    }
    return "/api/files/" + taskId + "/" + fileName;
  }
}

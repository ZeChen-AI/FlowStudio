package studio.flow.controller;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import studio.flow.dto.ResultResponse;
import studio.flow.dto.TaskResponse;
import studio.flow.model.EditTask;
import studio.flow.model.TaskStatus;
import studio.flow.service.SessionAuthService;
import studio.flow.service.TaskService;

@RestController
@RequestMapping("/api")
public class TaskController {
  private final TaskService taskService;
  private final SessionAuthService sessionAuthService;

  public TaskController(
      TaskService taskService, SessionAuthService sessionAuthService) {
    this.taskService = taskService;
    this.sessionAuthService = sessionAuthService;
  }

  @PostMapping(value = "/tasks/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public TaskResponse createEditTask(
      @RequestParam(value = "projectName", required = false) String projectName,
      @RequestParam(value = "sourcePrompt", required = false) String sourcePrompt,
      @RequestParam("targetPrompt") String targetPrompt,
      @RequestParam("targetWord") String targetWord,
      @RequestPart("video") MultipartFile video,
      @RequestPart("mask") MultipartFile mask,
      HttpSession session)
      throws IOException {
    String username = sessionAuthService.requireUsername(session);
    return toResponse(
        taskService.createEditTask(
            username, projectName, sourcePrompt, targetPrompt, targetWord, video, mask));
  }

  @GetMapping("/tasks/{taskId}")
  public TaskResponse getTask(@PathVariable String taskId, HttpSession session) {
    String username = sessionAuthService.requireUsername(session);
    return toResponse(
        taskService
            .findForUser(username, taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found.")));
  }

  @GetMapping("/tasks/{taskId}/status")
  public TaskResponse getTaskStatus(@PathVariable String taskId, HttpSession session) {
    return getTask(taskId, session);
  }

  @GetMapping("/tasks/{taskId}/result")
  public ResultResponse getTaskResult(
      @PathVariable String taskId, HttpSession session) {
    String username = sessionAuthService.requireUsername(session);
    EditTask task =
        taskService
            .findForUser(username, taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found."));

    if (task.getStatus() != TaskStatus.SUCCESS
        || task.getResultUrl() == null
        || task.getResultUrl().isBlank()) {
      throw new IllegalArgumentException("Result is not ready.");
    }

    return new ResultResponse(
        task.getTaskId(), task.getResultUrl(), task.getMessage());
  }

  @GetMapping("/files/{taskId}/{fileName}")
  public ResponseEntity<Resource> getTaskFile(
      @PathVariable String taskId,
      @PathVariable String fileName,
      HttpSession session)
      throws Exception {
    String username = sessionAuthService.requireUsername(session);
    Path file = taskService.resolveTaskFile(username, taskId, fileName);
    Resource resource = new UrlResource(file.toUri());

    String contentType = Files.probeContentType(file);
    MediaType mediaType =
        contentType == null
            ? MediaType.APPLICATION_OCTET_STREAM
            : MediaType.parseMediaType(contentType);

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore().cachePrivate())
        .contentType(mediaType)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(file.getFileName().toString())
                .build()
                .toString())
        .body(resource);
  }

  private TaskResponse toResponse(EditTask task) {
    return new TaskResponse(
        task.getTaskId(),
        task.getProjectName(),
        task.getSourcePrompt(),
        task.getTargetPrompt(),
        task.getTargetWord(),
        task.getStatus().name(),
        task.getResultUrl(),
        task.getMessage(),
        task.getErrorMessage());
  }
}

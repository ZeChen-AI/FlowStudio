package studio.flow.model;

import java.nio.file.Path;

/**
 * Mutable task state shared by HTTP requests and the background task runner.
 */
public class EditTask {
  private final String taskId;
  private volatile String username;
  private volatile String createdAt;
  private volatile String projectName;
  private volatile String sourcePrompt;
  private volatile String targetPrompt;
  private volatile String targetWord;
  private volatile TaskStatus status = TaskStatus.PENDING;
  private volatile Path taskDir;
  private volatile Path inputVideoPath;
  private volatile Path maskPath;
  private volatile Path resultVideoPath;
  private volatile String resultUrl;
  private volatile String message;
  private volatile String errorMessage;

  public EditTask(String taskId) {
    this.taskId = taskId;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public String getSourcePrompt() {
    return sourcePrompt;
  }

  public void setSourcePrompt(String sourcePrompt) {
    this.sourcePrompt = sourcePrompt;
  }

  public String getTargetPrompt() {
    return targetPrompt;
  }

  public void setTargetPrompt(String targetPrompt) {
    this.targetPrompt = targetPrompt;
  }

  public String getTargetWord() {
    return targetWord;
  }

  public void setTargetWord(String targetWord) {
    this.targetWord = targetWord;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public void setStatus(TaskStatus status) {
    this.status = status;
  }

  public Path getTaskDir() {
    return taskDir;
  }

  public void setTaskDir(Path taskDir) {
    this.taskDir = taskDir;
  }

  public Path getInputVideoPath() {
    return inputVideoPath;
  }

  public void setInputVideoPath(Path inputVideoPath) {
    this.inputVideoPath = inputVideoPath;
  }

  public Path getMaskPath() {
    return maskPath;
  }

  public void setMaskPath(Path maskPath) {
    this.maskPath = maskPath;
  }

  public Path getResultVideoPath() {
    return resultVideoPath;
  }

  public void setResultVideoPath(Path resultVideoPath) {
    this.resultVideoPath = resultVideoPath;
  }

  public String getResultUrl() {
    return resultUrl;
  }

  public void setResultUrl(String resultUrl) {
    this.resultUrl = resultUrl;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}

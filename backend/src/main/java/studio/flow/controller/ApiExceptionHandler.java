package studio.flow.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<Map<String, Object>> unauthorized(
      UnauthorizedException error) {
    return error(HttpStatus.UNAUTHORIZED, error.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> badRequest(
      IllegalArgumentException error) {
    return error(HttpStatus.BAD_REQUEST, error.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> unexpected(Exception error) {
    String message =
        error.getMessage() == null || error.getMessage().isBlank()
            ? "Internal server error."
            : error.getMessage();
    return error(HttpStatus.INTERNAL_SERVER_ERROR, message);
  }

  private ResponseEntity<Map<String, Object>> error(
      HttpStatus status, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", status.value());
    body.put("message", message);
    body.put("errorMessage", message);
    return ResponseEntity.status(status).body(body);
  }
}

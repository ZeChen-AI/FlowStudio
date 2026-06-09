package studio.flow.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.flow.config.FlowStudioProperties;
import studio.flow.dto.HealthResponse;

@RestController
public class HealthController {
  private final FlowStudioProperties properties;

  public HealthController(FlowStudioProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/api/health")
  public HealthResponse health() {
    String autodlBaseUrl = properties.getAutodlBaseUrl();
    boolean autodlConfigured = autodlBaseUrl != null && !autodlBaseUrl.isBlank();
    return new HealthResponse(
        "ok",
        properties.isMockRunner(),
        autodlBaseUrl == null ? "" : autodlBaseUrl,
        autodlConfigured);
  }
}

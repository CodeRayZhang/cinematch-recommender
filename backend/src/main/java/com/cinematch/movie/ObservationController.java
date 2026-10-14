package com.cinematch.movie;

import com.cinematch.auth.AccountMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ObservationController {
  private final AccountMapper accounts;
  private final RecommendationObservation observations;

  public ObservationController(AccountMapper accounts, RecommendationObservation observations) {
    this.accounts = accounts;
    this.observations = observations;
  }

  public record Event(
      @NotBlank @Size(max = 36) String requestId,
      @Positive long movieId,
      @NotNull @Pattern(regexp = "exposure|click") String event) {}

  public record Setting(@NotNull Boolean enabled) {}

  private long user(Principal p) {
    return accounts.findByUsername(p.getName()).id();
  }

  @PostMapping("/api/me/recommendation-events")
  public Map<String, String> event(Principal p, @Valid @RequestBody Event in) {
    observations.event(user(p), in.requestId(), in.movieId(), in.event());
    return Map.of("message", "已记录");
  }

  @GetMapping("/api/me/recommendation-settings")
  public Setting settings(Principal p) {
    return new Setting(observations.enabled(user(p)));
  }

  @PutMapping("/api/me/recommendation-settings")
  public Setting settings(Principal p, @Valid @RequestBody Setting in) {
    observations.setting(user(p), in.enabled());
    return in;
  }

  @DeleteMapping("/api/me/recommendation-history")
  public Map<String, String> clear(Principal p) {
    observations.clear(user(p));
    return Map.of("message", "推荐观察记录已清除，片单和评分保留");
  }

  @GetMapping("/api/admin/recommendation-metrics")
  public Map<String, Object> metrics(@RequestParam(defaultValue = "30") int days) {
    if (!Set.of(7, 30, 90).contains(days))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 7、30 或 90 天");
    return Map.of(
        "days",
        days,
        "algorithmVersion",
        RecommendationObservation.VERSION,
        "groups",
        observations.metrics(days),
        "attributionDays",
        7,
        "retainedRequestsPerUser",
        200);
  }
}

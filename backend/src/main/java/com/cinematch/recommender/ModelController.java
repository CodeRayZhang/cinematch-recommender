package com.cinematch.recommender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/recommender")
public class ModelController {
  private final ModelRegistry registry;

  public ModelController(ModelRegistry registry) {
    this.registry = registry;
  }

  public record Train(
      @NotBlank @Pattern(regexp = "local|[a-z0-9][a-z0-9-]{0,59}") String dataset,
      @Min(1) @Max(32) int rank,
      @Min(1) @Max(30) int iterations,
      @DecimalMin("0.01") @DecimalMax("10") double regularization,
      long seed) {}

  @GetMapping("/datasets")
  public Object datasets() {
    return registry.datasets();
  }

  @GetMapping("/models")
  public Object models() {
    return registry.list();
  }

  @PostMapping("/models")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Object train(@Valid @RequestBody Train request) {
    try {
      return Map.of(
          "id",
          registry.start(
              request.dataset(),
              new AlsModel.Parameters(
                  request.rank(), request.iterations(), request.regularization(), request.seed()),
              false));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  @PostMapping("/models/{id}/activate")
  public Object activate(@PathVariable String id) {
    registry.activate(id);
    return Map.of("message", "模型已启用，下一次推荐请求立即生效");
  }

  @PostMapping("/disable")
  public Object disable() {
    registry.disable();
    return Map.of("message", "已切回内容与邻域混合推荐");
  }

  @DeleteMapping("/models/{id}")
  public Object delete(@PathVariable String id) {
    registry.delete(id);
    return Map.of("message", "模型记录已删除，训练快照保留在服务器");
  }

  @GetMapping("/models/{id}/export")
  public Object export(@PathVariable String id) {
    return registry.export(id);
  }
}

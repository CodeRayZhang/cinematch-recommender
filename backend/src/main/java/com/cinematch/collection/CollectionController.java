package com.cinematch.collection;

import com.cinematch.auth.AccountMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me/collections")
public class CollectionController {
  private final AccountMapper accounts;
  private final CollectionService service;

  public CollectionController(AccountMapper accounts, CollectionService service) {
    this.accounts = accounts;
    this.service = service;
  }

  private long user(Principal p) {
    return accounts.findByUsername(p.getName()).id();
  }

  public record Input(
      @NotBlank @Size(max = 80) String name,
      @NotNull @Size(max = 1000) String description,
      @Positive Long coverMovieId) {}

  public record Movies(@NotEmpty @Size(max = 100) List<@NotNull @Positive Long> movieIds) {}

  public record Note(@NotNull @Size(max = 1000) String note) {}

  public record Move(@NotNull @Pattern(regexp = "up|down") String direction) {}

  private Map<String, String> ok() {
    return Map.of("message", "片单已更新");
  }

  @GetMapping
  public List<CollectionService.Shelf> list(Principal p) {
    return service.list(user(p));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CollectionService.Shelf create(Principal p, @Valid @RequestBody Input in) {
    return service.create(user(p), in.name(), in.description());
  }

  @PutMapping("/{id}")
  public CollectionService.Shelf update(
      Principal p, @PathVariable String id, @Valid @RequestBody Input in) {
    return service.update(user(p), id, in.name(), in.description(), in.coverMovieId());
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(Principal p, @PathVariable String id) {
    service.delete(user(p), id);
    return ok();
  }

  @GetMapping("/{id}")
  public CollectionService.Page page(
      Principal p,
      @PathVariable String id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "24") int size) {
    if (page < 1 || page > 10000 || size < 1 || size > 48)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
    return service.page(user(p), id, page, size);
  }

  @GetMapping("/{id}/export")
  public CollectionService.Export export(Principal p, @PathVariable String id) {
    return service.export(user(p), id);
  }

  @PostMapping("/{id}/movies")
  public Map<String, String> add(
      Principal p, @PathVariable String id, @Valid @RequestBody Movies in) {
    service.add(user(p), id, in.movieIds());
    return ok();
  }

  @DeleteMapping("/{id}/movies/{movie}")
  public Map<String, String> remove(
      Principal p, @PathVariable String id, @PathVariable long movie) {
    service.remove(user(p), id, movie);
    return ok();
  }

  @PutMapping("/{id}/movies/{movie}")
  public Map<String, String> note(
      Principal p, @PathVariable String id, @PathVariable long movie, @Valid @RequestBody Note in) {
    service.note(user(p), id, movie, in.note());
    return ok();
  }

  @PostMapping("/{id}/movies/{movie}/move")
  public Map<String, String> move(
      Principal p, @PathVariable String id, @PathVariable long movie, @Valid @RequestBody Move in) {
    service.move(user(p), id, movie, in.direction());
    return ok();
  }
}

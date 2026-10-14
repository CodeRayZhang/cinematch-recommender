package com.cinematch.movie;

import com.cinematch.auth.ProfileController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TopicController {
  private final JdbcTemplate jdbc;

  public TopicController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Input(
      @NotBlank @Size(max = 80) String title,
      @NotNull @Size(max = 240) String description,
      @NotNull @Size(max = 30) String genre,
      @Min(0) @Max(2100) int yearFrom,
      @Min(0) @Max(2100) int yearTo,
      @Min(0) @Max(180) int maxRuntime,
      @Min(0) @Max(100) int position,
      boolean enabled) {}

  public record Topic(
      String id,
      String title,
      String description,
      String genre,
      int yearFrom,
      int yearTo,
      int maxRuntime,
      int position,
      boolean enabled) {}

  private List<Topic> list(boolean all) {
    return jdbc.query(
        "SELECT * FROM catalog_topics"
            + (all ? "" : " WHERE enabled=TRUE")
            + " ORDER BY position,id LIMIT 50",
        (r, n) ->
            new Topic(
                r.getString("id"),
                r.getString("title"),
                r.getString("description"),
                r.getString("genre"),
                r.getInt("year_from"),
                r.getInt("year_to"),
                r.getInt("max_runtime"),
                r.getInt("position"),
                r.getBoolean("enabled")));
  }

  @GetMapping("/api/topics")
  public List<Topic> published() {
    return list(false);
  }

  @GetMapping("/api/admin/topics")
  public List<Topic> all() {
    return list(true);
  }

  private void validate(Input in) {
    if ((!in.genre().isEmpty() && !ProfileController.GENRES.contains(in.genre()))
        || (in.yearFrom() != 0 && in.yearFrom() < 1888)
        || (in.yearTo() != 0 && in.yearTo() < 1888)
        || (in.yearFrom() > 0 && in.yearTo() > 0 && in.yearFrom() > in.yearTo())
        || !Set.of(0, 90, 120, 180).contains(in.maxRuntime()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "专题筛选条件无效");
  }

  @PostMapping("/api/admin/topics")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, String> create(@Valid @RequestBody Input in) {
    validate(in);
    if (jdbc.queryForObject("SELECT COUNT(*) FROM catalog_topics", Long.class) >= 50)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多保存 50 个专题");
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " catalog_topics(id,title,description,genre,year_from,year_to,max_runtime,position,enabled)"
            + " VALUES(?,?,?,?,?,?,?,?,?)",
        id,
        in.title().strip(),
        in.description().strip(),
        in.genre(),
        in.yearFrom(),
        in.yearTo(),
        in.maxRuntime(),
        in.position(),
        in.enabled());
    return Map.of("id", id);
  }

  @PutMapping("/api/admin/topics/{id}")
  public Map<String, String> update(@PathVariable String id, @Valid @RequestBody Input in) {
    validate(in);
    if (jdbc.update(
            "UPDATE catalog_topics SET"
                + " title=?,description=?,genre=?,year_from=?,year_to=?,max_runtime=?,position=?,enabled=?"
                + " WHERE id=?",
            in.title().strip(),
            in.description().strip(),
            in.genre(),
            in.yearFrom(),
            in.yearTo(),
            in.maxRuntime(),
            in.position(),
            in.enabled(),
            id)
        == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "专题不存在");
    return Map.of("message", "专题已更新");
  }

  @DeleteMapping("/api/admin/topics/{id}")
  public Map<String, String> delete(@PathVariable String id) {
    jdbc.update("DELETE FROM catalog_topics WHERE id=?", id);
    return Map.of("message", "专题已删除");
  }
}

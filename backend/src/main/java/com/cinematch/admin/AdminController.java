package com.cinematch.admin;

import com.cinematch.movie.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final MovieMapper movies;
  private final JdbcTemplate jdbc;

  public AdminController(MovieMapper movies, JdbcTemplate jdbc) {
    this.movies = movies;
    this.jdbc = jdbc;
  }

  @GetMapping("/stats")
  public Map<String, Long> stats() {
    return Map.of(
        "movies",
        count("movies"),
        "users",
        count("users"),
        "reviews",
        count("reviews"),
        "saved",
        count("library_entries"));
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  @GetMapping("/users")
  public List<Map<String, Object>> users() {
    return jdbc.queryForList(
        "SELECT id,username,display_name,role,created_at FROM users ORDER BY id DESC LIMIT 100");
  }

  @PostMapping("/movies")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, String> create(@Valid @RequestBody Input input) {
    movies.insert(input.movie(0));
    return Map.of("message", "电影已添加");
  }

  @PutMapping("/movies/{id}")
  public Map<String, String> update(@PathVariable long id, @Valid @RequestBody Input input) {
    if (movies.update(input.movie(id)) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "电影不存在");
    return Map.of("message", "电影已更新");
  }

  @DeleteMapping("/movies/{id}")
  public Map<String, String> delete(@PathVariable long id) {
    if (movies.delete(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "电影不存在");
    return Map.of("message", "电影已删除");
  }

  public record Input(
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 200) String originalTitle,
      @Min(1888) @Max(2100) int releaseYear,
      @NotBlank @Size(max = 160) @Pattern(regexp = "[^,]+(,[^,]+)*") String genres,
      @Min(1) @Max(1440) int runtime,
      @DecimalMin("0") @DecimalMax("10") double rating,
      @Min(0) int ratingCount,
      @NotBlank @Size(max = 160) String director,
      @NotBlank @Size(max = 100) String country,
      @NotBlank @Size(max = 6000) String synopsis,
      @NotBlank @Size(max = 1000) @Pattern(regexp = "^(https://[^\\s]+|/images/[a-zA-Z0-9._/-]+)$")
          String posterUrl,
      @NotBlank @Size(max = 1000) @Pattern(regexp = "^(https://[^\\s]+|/images/[a-zA-Z0-9._/-]+)$")
          String backdropUrl,
      boolean featured) {
    Movie movie(long id) {
      return new Movie(
          id,
          title.strip(),
          originalTitle.strip(),
          releaseYear,
          String.join(",", Arrays.stream(genres.split(",")).map(String::strip).distinct().toList()),
          runtime,
          rating,
          ratingCount,
          director.strip(),
          country.strip(),
          synopsis.strip(),
          posterUrl,
          backdropUrl,
          featured,
          "manual",
          null,
          null,
          null);
    }
  }
}

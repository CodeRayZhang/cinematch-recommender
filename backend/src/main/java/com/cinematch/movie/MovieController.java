package com.cinematch.movie;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/movies")
public class MovieController {
  private final MovieMapper movies;

  public MovieController(MovieMapper movies) {
    this.movies = movies;
  }

  @GetMapping
  public Page search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String genre,
      @RequestParam(defaultValue = "popular") String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int size) {
    if (page < 1
        || page > 10000
        || size < 1
        || size > 48
        || q.length() > 100
        || genre.length() > 30
        || !Set.of("popular", "rating", "recent").contains(sort))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "筛选条件不正确");
    return new Page(
        movies.search(q.strip(), genre, sort, size, (page - 1) * size),
        movies.count(q.strip(), genre),
        page,
        size);
  }

  @GetMapping("/featured")
  public List<Movie> featured() {
    return movies.featured();
  }

  @GetMapping("/{id}")
  public Movie detail(@PathVariable long id) {
    return require(movies, id);
  }

  @GetMapping("/{id}/related")
  public List<Movie> related(@PathVariable long id) {
    var movie = require(movies, id);
    var genres = Set.of(movie.genres().split(","));
    return movies.candidates().stream()
        .filter(m -> m.id() != id)
        .sorted(
            Comparator.<Movie>comparingLong(
                    m -> Arrays.stream(m.genres().split(",")).filter(genres::contains).count())
                .reversed()
                .thenComparing(Comparator.comparingDouble(Movie::rating).reversed()))
        .limit(6)
        .toList();
  }

  public static Movie require(MovieMapper movies, long id) {
    var movie = movies.find(id);
    if (movie == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "这部电影暂时不在片库中");
    return movie;
  }

  public record Page(List<Movie> items, long total, int page, int size) {}
}

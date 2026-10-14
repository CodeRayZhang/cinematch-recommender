package com.cinematch.library;

import com.cinematch.auth.AccountMapper;
import com.cinematch.movie.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LibraryController {
  private final LibraryMapper library;
  private final AccountMapper accounts;
  private final MovieMapper movies;

  public LibraryController(LibraryMapper library, AccountMapper accounts, MovieMapper movies) {
    this.library = library;
    this.accounts = accounts;
    this.movies = movies;
  }

  @GetMapping("/me/library")
  public Map<String, Object> library(Principal user) {
    long id = accounts.findByUsername(user.getName()).id();
    return Map.of(
        "entries",
        library.entries(id),
        "movies",
        library.preferenceMovies(id),
        "reviews",
        library.reviews(id));
  }

  @PutMapping("/me/library/{movieId}")
  public Map<String, String> save(
      Principal user, @PathVariable long movieId, @Valid @RequestBody Status input) {
    MovieController.require(movies, movieId);
    library.save(accounts.findByUsername(user.getName()).id(), movieId, input.status());
    return Map.of("message", "片单已更新");
  }

  @DeleteMapping("/me/library/{movieId}")
  public Map<String, String> remove(Principal user, @PathVariable long movieId) {
    library.remove(accounts.findByUsername(user.getName()).id(), movieId);
    return Map.of("message", "已从片单移除");
  }

  @PutMapping("/me/library/batch")
  @Transactional
  public Map<String, String> batch(Principal user, @Valid @RequestBody Batch input) {
    var ids = new LinkedHashSet<>(input.movieIds());
    // Validate the entire batch before changing any rows.
    ids.forEach(id -> MovieController.require(movies, id));
    long userId = accounts.findByUsername(user.getName()).id();
    for (long id : ids) {
      if (input.action().equals("REMOVE")) library.remove(userId, id);
      else library.save(userId, id, input.action());
    }
    return Map.of("message", "已更新 " + ids.size() + " 部电影");
  }

  @DeleteMapping("/me/reviews/{movieId}")
  public Map<String, String> deleteReview(Principal user, @PathVariable long movieId) {
    library.removeReview(accounts.findByUsername(user.getName()).id(), movieId);
    return Map.of("message", "评分和短评已删除，看过状态保留");
  }

  public record Batch(
      @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> movieIds,
      @NotNull @Pattern(regexp = "WATCHLIST|WATCHED|REMOVE") String action) {}

  @PutMapping("/me/reviews/{movieId}")
  @Transactional
  public Map<String, String> review(
      Principal user, @PathVariable long movieId, @Valid @RequestBody Rating input) {
    MovieController.require(movies, movieId);
    long id = accounts.findByUsername(user.getName()).id();
    library.review(id, movieId, input.stars(), input.body().strip());
    library.save(id, movieId, "WATCHED");
    return Map.of("message", "已记录你的观影感受");
  }

  @GetMapping("/movies/{movieId}/reviews")
  public List<LibraryMapper.PublicReview> reviews(@PathVariable long movieId) {
    MovieController.require(movies, movieId);
    return library.publicReviews(movieId);
  }

  public record Status(@NotNull @Pattern(regexp = "WATCHLIST|WATCHED") String status) {}

  public record Rating(@Min(1) @Max(5) int stars, @NotNull @Size(max = 2000) String body) {}
}

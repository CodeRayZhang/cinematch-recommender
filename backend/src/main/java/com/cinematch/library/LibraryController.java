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
        library.movies(id),
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

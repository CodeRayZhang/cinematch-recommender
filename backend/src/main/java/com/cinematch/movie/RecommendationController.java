package com.cinematch.movie;

import com.cinematch.auth.AccountMapper;
import com.cinematch.library.LibraryMapper;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class RecommendationController {
  private final MovieMapper movies;
  private final LibraryMapper library;
  private final AccountMapper accounts;
  private final RecommendationService service;

  public RecommendationController(
      MovieMapper movies,
      LibraryMapper library,
      AccountMapper accounts,
      RecommendationService service) {
    this.movies = movies;
    this.library = library;
    this.accounts = accounts;
    this.service = service;
  }

  @GetMapping("/api/recommendations")
  public List<RecommendationService.Pick> recommendations(Principal principal) {
    if (principal == null)
      return service.rank(movies.candidates(), List.of(), List.of(), List.of());
    long id = accounts.findByUsername(principal.getName()).id();
    return service.rank(
        movies.candidates(),
        library.preferenceMovies(id),
        library.entries(id),
        library.reviews(id));
  }
}

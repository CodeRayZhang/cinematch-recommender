package com.cinematch.movie;

import com.cinematch.auth.*;
import com.cinematch.library.LibraryMapper;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RecommendationController {
  private final CatalogCache movies;
  private final LibraryMapper library;
  private final AccountMapper accounts;
  private final RecommendationService service;
  private final ProfileMapper profiles;
  private final FeedbackMapper feedback;
  private final RecommendationObservation observations;

  public RecommendationController(
      CatalogCache movies,
      LibraryMapper library,
      AccountMapper accounts,
      RecommendationService service,
      ProfileMapper profiles,
      FeedbackMapper feedback,
      RecommendationObservation observations) {
    this.movies = movies;
    this.library = library;
    this.accounts = accounts;
    this.service = service;
    this.profiles = profiles;
    this.feedback = feedback;
    this.observations = observations;
  }

  public record Pick(Movie movie, String reason, boolean exploration, String requestId) {}

  @GetMapping("/api/recommendations")
  public List<Pick> recommendations(
      Principal principal,
      @RequestParam(defaultValue = "balanced") String mode,
      @RequestParam(defaultValue = "") String genres,
      @RequestParam(defaultValue = "0") int maxRuntime,
      @RequestParam(defaultValue = "") String exclude) {
    if (!Set.of("balanced", "explore").contains(mode)) throw bad("请选择有效的推荐模式");
    if (!Set.of(0, 90, 120, 180).contains(maxRuntime)) throw bad("请选择有效的观影时长");
    Set<String> selected = new LinkedHashSet<>();
    if (!genres.isBlank()) selected.addAll(Arrays.asList(genres.split(",", -1)));
    if (selected.size() > 3 || !ProfileController.GENRES.containsAll(selected))
      throw bad("最多选择三种有效电影类型");
    Set<Long> skipped = new HashSet<>();
    if (!exclude.isBlank()) {
      var parts = exclude.split(",", -1);
      if (parts.length > 120) throw bad("一次最多排除 120 部电影，请重新开始推荐");
      try {
        for (String part : parts) {
          long id = Long.parseLong(part);
          if (id <= 0) throw new NumberFormatException();
          skipped.add(id);
        }
      } catch (NumberFormatException e) {
        throw bad("电影编号格式不正确");
      }
    }
    var options =
        new RecommendationService.Options(mode.equals("explore"), selected, maxRuntime, skipped);
    List<RecommendationService.Pick> result;
    String requestId = null;
    if (principal == null)
      result =
          service.rank(
              movies.candidates(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              Set.of(),
              List.of(),
              options);
    else {
      long id = accounts.findByUsername(principal.getName()).id();
      var reviews = library.reviews(id);
      result =
          service.rank(
              movies.candidates(),
              library.preferenceMovies(id),
              library.entries(id),
              reviews,
              Arrays.stream(profiles.find(id).preferredGenres().split(","))
                  .filter(g -> !g.isBlank())
                  .toList(),
              new HashSet<>(feedback.hiddenIds(id)),
              library.neighborRatings(id),
              options);
      requestId = observations.record(id, mode, reviews.size(), result);
    }
    String token = requestId;
    return result.stream()
        .map(p -> new Pick(p.movie(), p.reason(), p.exploration(), token))
        .toList();
  }

  private ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}

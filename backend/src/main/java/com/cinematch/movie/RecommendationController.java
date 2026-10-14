package com.cinematch.movie;

import com.cinematch.auth.*;
import com.cinematch.library.LibraryMapper;
import com.cinematch.recommender.ModelPersonalizer;
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
  private final ModelPersonalizer models;

  public RecommendationController(
      CatalogCache movies,
      LibraryMapper library,
      AccountMapper accounts,
      RecommendationService service,
      ProfileMapper profiles,
      FeedbackMapper feedback,
      RecommendationObservation observations,
      ModelPersonalizer models) {
    this.movies = movies;
    this.library = library;
    this.accounts = accounts;
    this.service = service;
    this.profiles = profiles;
    this.feedback = feedback;
    this.observations = observations;
    this.models = models;
  }

  public record Pick(
      Movie movie,
      String reason,
      boolean exploration,
      String requestId,
      String engine,
      String modelVersion,
      boolean modelApplied) {}

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
    ModelPersonalizer.Context context = new ModelPersonalizer.Context(null, 0, 0, Map.of());
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
      var history = library.preferenceMovies(id);
      context = models.context(movies.candidates(), history, reviews);
      result =
          service.rank(
              movies.candidates(),
              history,
              library.entries(id),
              reviews,
              Arrays.stream(profiles.find(id).preferredGenres().split(","))
                  .filter(g -> !g.isBlank())
                  .toList(),
              new HashSet<>(feedback.hiddenIds(id)),
              library.neighborRatings(id),
              options,
              context.scores());
      requestId = observations.record(id, mode, reviews.size(), result, context.version());
    }
    String token = requestId;
    var current = context;
    return result.stream()
        .map(
            p ->
                new Pick(
                    p.movie(),
                    p.reason(),
                    p.exploration(),
                    token,
                    current.engine(),
                    current.ready() ? current.modelVersion() : null,
                    current.scores().containsKey(p.movie().id())))
        .toList();
  }

  @GetMapping("/api/recommendations/status")
  public Object status(Principal principal) {
    List<LibraryMapper.Review> reviews = List.of();
    List<Movie> history = List.of();
    if (principal != null) {
      long id = accounts.findByUsername(principal.getName()).id();
      reviews = library.reviews(id);
      history = library.preferenceMovies(id);
    }
    var context = models.context(movies.candidates(), history, reviews);
    Set<Long> rated = new HashSet<>();
    reviews.forEach(r -> rated.add(r.movieId()));
    Map<String, Integer> genres = new TreeMap<>();
    for (var movie : history)
      if (rated.contains(movie.id()))
        for (String genre : movie.genres().split(","))
          if (!genre.isBlank()) genres.merge(genre.strip(), 1, Integer::sum);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
        "tasteGenres",
        genres.entrySet().stream()
            .map(e -> Map.of("name", e.getKey(), "count", e.getValue()))
            .toList());
    result.put("model", models.details(context.modelVersion()));
    result.put("engine", context.engine());
    result.put("modelVersion", context.modelVersion());
    result.put("mappedRatings", context.mappedRatings());
    result.put("mappedMovies", context.mappedMovies());
    result.put("minimumRatings", ModelPersonalizer.MINIMUM_RATINGS);
    result.put("ready", context.ready());
    result.put(
        "message",
        context.ready()
            ? "协同模型已根据你的最新评分参与推荐"
            : context.modelVersion() == null
                ? "当前使用内容偏好与相似用户推荐"
                : "为至少 " + ModelPersonalizer.MINIMUM_RATINGS + " 部模型覆盖的电影评分，建立你的观影口味");
    return result;
  }

  @GetMapping("/api/me/recommendations/starters")
  public Object starters(Principal principal) {
    long id = accounts.findByUsername(principal.getName()).id();
    return models.starters(
        movies.candidates(), library.reviews(id), new HashSet<>(feedback.hiddenIds(id)));
  }

  private ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}

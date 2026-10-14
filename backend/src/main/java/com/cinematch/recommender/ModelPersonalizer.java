package com.cinematch.recommender;

import com.cinematch.library.LibraryMapper;
import com.cinematch.movie.Movie;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ModelPersonalizer {
  public static final int MINIMUM_RATINGS = 3;
  public static final double MODEL_WEIGHT = .45;

  public record Context(
      String modelVersion, int mappedRatings, int mappedMovies, Map<Long, Double> scores) {
    public boolean ready() {
      return modelVersion != null && mappedRatings >= MINIMUM_RATINGS && !scores.isEmpty();
    }

    public String engine() {
      return ready() ? "als-hybrid" : "hybrid";
    }

    public String version() {
      return ready() ? "als-v1:" + modelVersion : "hybrid-v2.1";
    }
  }

  private final ModelRegistry registry;

  public ModelPersonalizer(ModelRegistry registry) {
    this.registry = registry;
  }

  public Map<String, Integer> details(String version) {
    var loaded = registry.active();
    if (loaded == null || !loaded.id().equals(version)) return Map.of();
    return Map.of(
        "rank",
        loaded.model().artifact().parameters().rank(),
        "trainingItems",
        loaded.model().artifact().itemIds().length);
  }

  public Context context(
      List<Movie> candidates, List<Movie> history, List<LibraryMapper.Review> reviews) {
    var loaded = registry.active();
    if (loaded == null) return new Context(null, 0, 0, Map.of());
    Map<Long, Movie> catalog = new HashMap<>();
    candidates.forEach(m -> catalog.put(m.id(), m));
    history.forEach(m -> catalog.put(m.id(), m));
    Map<Long, Long> links = new HashMap<>();
    for (var map : loaded.mappings())
      if (catalog.containsKey(map.movieId())
          && map.matches(catalog.get(map.movieId()))
          && loaded.model().contains(map.itemId())) links.put(map.movieId(), map.itemId());
    Map<Long, Double> own = new HashMap<>();
    for (var r : reviews)
      if (links.containsKey(r.movieId())) own.put(links.get(r.movieId()), (double) r.stars());
    Map<Long, Double> scores = new HashMap<>();
    if (own.size() >= MINIMUM_RATINGS) {
      var values = loaded.model().predictions(own);
      links.forEach((local, external) -> scores.put(local, values.get(external)));
    }
    return new Context(loaded.id(), own.size(), links.size(), Map.copyOf(scores));
  }

  public List<Movie> starters(
      List<Movie> candidates, List<LibraryMapper.Review> reviews, Set<Long> hidden) {
    Set<Long> excluded = new HashSet<>(hidden);
    reviews.forEach(r -> excluded.add(r.movieId()));
    var loaded = registry.active();
    Map<Long, TrainingData.Mapping> links = new HashMap<>();
    if (loaded != null) loaded.mappings().forEach(m -> links.put(m.movieId(), m));
    var pool =
        new ArrayList<>(
            candidates.stream()
                .filter(m -> !excluded.contains(m.id()))
                .filter(
                    m ->
                        loaded == null
                            || (links.containsKey(m.id())
                                && links.get(m.id()).matches(m)
                                && loaded.model().contains(links.get(m.id()).itemId())))
                .sorted(
                    Comparator.comparingInt(Movie::ratingCount)
                        .reversed()
                        .thenComparingLong(Movie::id))
                .toList());
    List<Movie> selected = new ArrayList<>();
    Map<String, Integer> genres = new HashMap<>();
    while (selected.size() < 12 && !pool.isEmpty()) {
      Movie best =
          pool.stream()
              .min(
                  Comparator.<Movie>comparingInt(
                          m ->
                              Arrays.stream(m.genres().split(","))
                                  .mapToInt(g -> genres.getOrDefault(g, 0))
                                  .sum())
                      .thenComparing(Comparator.comparingInt(Movie::ratingCount).reversed())
                      .thenComparingLong(Movie::id))
              .orElseThrow();
      selected.add(best);
      pool.remove(best);
      for (String g : best.genres().split(",")) genres.merge(g, 1, Integer::sum);
    }
    return selected;
  }
}

package com.cinematch;

import com.cinematch.library.LibraryMapper;
import com.cinematch.movie.Movie;
import java.util.*;
import java.util.stream.Collectors;

/** Bounded content / neighbor hybrid, with an explicit cold-start fallback. */
class PreviousHybridRecommender {
  public record Pick(Movie movie, String reason) {}

  private record Scored(Movie movie, double score, String reason) {}

  private record Neighbor(Map<Long, Integer> ratings, double mean, double similarity) {}

  public List<Pick> rank(
      List<Movie> candidates,
      List<Movie> saved,
      List<LibraryMapper.Entry> entries,
      List<LibraryMapper.Review> reviews) {
    return rank(candidates, saved, entries, reviews, List.of(), Set.of(), List.of());
  }

  public List<Pick> rank(
      List<Movie> candidates,
      List<Movie> saved,
      List<LibraryMapper.Entry> entries,
      List<LibraryMapper.Review> reviews,
      List<String> preferredGenres,
      Set<Long> hidden,
      List<LibraryMapper.Signal> peers) {
    Map<Long, Integer> ratings = new HashMap<>();
    reviews.forEach(r -> ratings.put(r.movieId(), r.stars()));
    Map<Long, String> statuses = new HashMap<>();
    entries.forEach(e -> statuses.put(e.movieId(), e.status()));
    Set<Long> excluded = new HashSet<>(hidden);
    excluded.addAll(statuses.keySet());
    excluded.addAll(ratings.keySet());
    // Use the same feature space for candidates and interaction records outside the candidate cap.
    Map<Long, Movie> catalog = new LinkedHashMap<>();
    candidates.forEach(m -> catalog.put(m.id(), m));
    saved.forEach(m -> catalog.put(m.id(), m));
    Map<String, Integer> genreCounts = new HashMap<>();
    catalog
        .values()
        .forEach(m -> tokens(m.genres()).forEach(g -> genreCounts.merge(g, 1, Integer::sum)));
    Map<Long, Map<String, Double>> vectors = new HashMap<>();
    catalog.values().forEach(m -> vectors.put(m.id(), features(m, genreCounts, catalog.size())));
    Map<String, Double> positive = new HashMap<>(),
        negative = new HashMap<>(),
        genreAffinity = new HashMap<>(),
        directorAffinity = new HashMap<>();
    for (Movie m : saved) {
      Integer stars = ratings.get(m.id());
      double weight =
          stars != null ? (stars - 3) : "WATCHLIST".equals(statuses.get(m.id())) ? .45 : .1;
      if (weight == 0) continue;
      var target = weight > 0 ? positive : negative;
      vectors.get(m.id()).forEach((f, v) -> target.merge(f, Math.abs(weight) * v, Double::sum));
      tokens(m.genres()).forEach(g -> genreAffinity.merge(g, weight, Double::sum));
      tokens(m.director()).forEach(d -> directorAffinity.merge(d, weight, Double::sum));
    }
    for (String g : preferredGenres) {
      positive.merge("g:" + g, 1.25, Double::sum);
      genreAffinity.merge(g, 1.25, Double::sum);
    }
    normalize(positive);
    normalize(negative);
    Map<Long, Double> collaborative = collaborative(ratings, peers);
    List<Scored> remaining = new ArrayList<>();
    for (Movie m : candidates) {
      if (excluded.contains(m.id())) continue;
      Map<String, Double> vector = vectors.get(m.id());
      double content = dot(vector, positive) - .8 * dot(vector, negative);
      double neighbor = collaborative.getOrDefault(m.id(), 0.0);
      double quality =
          (m.rating() * Math.max(0, m.ratingCount()) + 7.5 * 1000)
              / (Math.max(0, m.ratingCount()) + 1000.0)
              / 10;
      double score = .30 * quality + .70 * content + .65 * neighbor;
      String genre =
          tokens(m.genres()).stream()
              .filter(g -> genreAffinity.getOrDefault(g, 0.0) > 0)
              .sorted(
                  Comparator.<String>comparingDouble(g -> genreAffinity.get(g))
                      .reversed()
                      .thenComparing(g -> g))
              .findFirst()
              .orElse(null);
      String director =
          tokens(m.director()).stream()
              .filter(d -> directorAffinity.getOrDefault(d, 0.0) > 0)
              .findFirst()
              .orElse(null);
      String reason =
          neighbor > .15
              ? "与你评分相近的影迷也喜欢"
              : genre != null && content > 0
                  ? "因为你偏爱" + genre + "电影"
                  : director != null && content > 0
                      ? "延续你喜欢的导演：" + director
                      : negative.isEmpty() ? "从值得一看的电影开始" : "换一种故事，也许更合你意";
      remaining.add(new Scored(m, score, reason));
    }
    List<Scored> selected = new ArrayList<>();
    Map<String, Integer> selectedDirectors = new HashMap<>();
    // MMR-style reranking: retain relevance while avoiding a wall of nearly identical films.
    while (selected.size() < 12 && !remaining.isEmpty()) {
      Scored best = null;
      double bestScore = Double.NEGATIVE_INFINITY;
      // Prefer at most two films per credited director; relax only when alternatives run out.
      var eligible =
          remaining.stream()
              .filter(
                  item ->
                      tokens(item.movie.director()).stream()
                          .noneMatch(d -> selectedDirectors.getOrDefault(d, 0) >= 2))
              .toList();
      if (eligible.isEmpty()) eligible = remaining;
      for (Scored item : eligible) {
        double similarity = 0;
        for (Scored chosen : selected)
          similarity =
              Math.max(
                  similarity, dot(vectors.get(item.movie.id()), vectors.get(chosen.movie.id())));
        double adjusted = item.score - .08 * similarity;
        if (best == null
            || adjusted > bestScore
            || (adjusted == bestScore && item.movie.id() < best.movie.id())) {
          best = item;
          bestScore = adjusted;
        }
      }
      tokens(best.movie.director()).forEach(d -> selectedDirectors.merge(d, 1, Integer::sum));
      selected.add(best);
      remaining.remove(best);
    }
    return selected.stream().map(s -> new Pick(s.movie, s.reason)).toList();
  }

  private Map<Long, Double> collaborative(
      Map<Long, Integer> own, List<LibraryMapper.Signal> signals) {
    if (own.size() < 3 || signals.isEmpty()) return Map.of();
    Map<Long, Map<Long, Integer>> peers = new HashMap<>();
    signals.forEach(
        s -> peers.computeIfAbsent(s.userId(), id -> new HashMap<>()).put(s.movieId(), s.stars()));
    List<Neighbor> neighbors = new ArrayList<>();
    for (var peer : peers.values()) {
      List<Long> common = own.keySet().stream().filter(peer::containsKey).toList();
      if (common.size() < 3) continue;
      double a = common.stream().mapToDouble(own::get).average().orElse(3);
      double b = common.stream().mapToDouble(peer::get).average().orElse(3);
      double product = 0, left = 0, right = 0;
      for (long id : common) {
        double x = own.get(id) - a, y = peer.get(id) - b;
        product += x * y;
        left += x * x;
        right += y * y;
      }
      if (left == 0 || right == 0) continue;
      double similarity = product / Math.sqrt(left * right) * common.size() / (common.size() + 8.0);
      if (similarity <= .05) continue;
      neighbors.add(
          new Neighbor(
              peer,
              peer.values().stream().mapToDouble(Integer::doubleValue).average().orElse(3),
              similarity));
    }
    neighbors.sort(
        Comparator.comparingDouble(Neighbor::similarity)
            .reversed()
            .thenComparing(n -> n.ratings.keySet().stream().min(Long::compareTo).orElse(0L)));
    Map<Long, Double> sums = new HashMap<>(), weights = new HashMap<>();
    Map<Long, Integer> counts = new HashMap<>();
    neighbors.stream()
        .limit(40)
        .forEach(
            n ->
                n.ratings.forEach(
                    (id, stars) -> {
                      sums.merge(id, n.similarity * (stars - n.mean), Double::sum);
                      weights.merge(id, n.similarity, Double::sum);
                      counts.merge(id, 1, Integer::sum);
                    }));
    Map<Long, Double> result = new HashMap<>();
    sums.forEach(
        (id, sum) -> {
          if (counts.get(id) >= 2)
            result.put(id, Math.max(-1, Math.min(1, sum / (weights.get(id) + 1.5))));
        });
    return result;
  }

  private Map<String, Double> features(Movie m, Map<String, Integer> counts, int size) {
    Map<String, Double> values = new HashMap<>();
    tokens(m.genres())
        .forEach(
            g ->
                values.put(
                    "g:" + g,
                    1 + Math.log((size + 1.0) / (counts.getOrDefault(g, 0) + 1.0)) * .35));
    tokens(m.director()).forEach(d -> values.put("d:" + d, .85));
    tokens(m.country()).forEach(c -> values.put("c:" + c, .15));
    if (m.releaseYear() > 0) values.put("y:" + (m.releaseYear() / 10), .2);
    normalize(values);
    return values;
  }

  private static Set<String> tokens(String value) {
    if (value == null || value.isBlank()) return Set.of();
    return Arrays.stream(value.split("[,/、|]"))
        .map(String::strip)
        .filter(s -> !s.isBlank())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static void normalize(Map<String, Double> vector) {
    double norm = Math.sqrt(vector.values().stream().mapToDouble(v -> v * v).sum());
    if (norm > 0) vector.replaceAll((key, value) -> value / norm);
  }

  private static double dot(Map<String, Double> a, Map<String, Double> b) {
    return a.entrySet().stream()
        .mapToDouble(e -> e.getValue() * b.getOrDefault(e.getKey(), 0.0))
        .sum();
  }
}

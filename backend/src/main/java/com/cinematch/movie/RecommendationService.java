package com.cinematch.movie;

import com.cinematch.library.LibraryMapper;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
  public record Pick(Movie movie, String reason) {}

  public List<Pick> rank(
      List<Movie> candidates,
      List<Movie> saved,
      List<LibraryMapper.Entry> entries,
      List<LibraryMapper.Review> reviews) {
    Map<Long, Integer> ratings = new HashMap<>();
    reviews.forEach(r -> ratings.put(r.movieId(), r.stars()));
    Map<String, Double> weights = new HashMap<>();
    for (Movie m : saved) {
      double weight = ratings.containsKey(m.id()) ? (ratings.get(m.id()) - 3) * 1.5 : 1;
      for (String genre : m.genres().split(",")) weights.merge(genre, weight, Double::sum);
    }
    Set<Long> excluded = new HashSet<>();
    entries.forEach(e -> excluded.add(e.movieId()));
    reviews.forEach(r -> excluded.add(r.movieId()));
    return candidates.stream()
        .filter(m -> !excluded.contains(m.id()))
        .sorted(
            Comparator.<Movie>comparingDouble(m -> score(m, weights))
                .reversed()
                .thenComparingLong(Movie::id))
        .limit(12)
        .map(
            m -> {
              String genre =
                  Arrays.stream(m.genres().split(","))
                      .filter(g -> weights.getOrDefault(g, 0.0) > 0)
                      .max(Comparator.comparingDouble(g -> weights.getOrDefault(g, 0.0)))
                      .orElse(null);
              return new Pick(m, genre == null ? "从值得一看的电影开始" : "因为你偏爱" + genre + "电影");
            })
        .toList();
  }

  private double score(Movie movie, Map<String, Double> weights) {
    double prior =
        (movie.rating() * movie.ratingCount() + 7.5 * 1000) / (movie.ratingCount() + 1000.0);
    return prior
        + Arrays.stream(movie.genres().split(","))
            .mapToDouble(g -> weights.getOrDefault(g, 0.0))
            .sum();
  }
}

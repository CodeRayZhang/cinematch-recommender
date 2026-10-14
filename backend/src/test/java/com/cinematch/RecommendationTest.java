package com.cinematch;

import static org.junit.jupiter.api.Assertions.*;

import com.cinematch.library.LibraryMapper;
import com.cinematch.movie.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RecommendationTest {
  Movie movie(long id, String genres, double rating) {
    return new Movie(
        id, "电影" + id, "Movie", 2026, genres, 100, rating, 1000, "导演", "中国", "简介", "/p", "/b",
        false, "demo", null, null, null);
  }

  @Test
  void preferencesChangeRankingAndSeenMoviesAreExcluded() {
    var seen = movie(1, "科幻", 8);
    var science = movie(2, "科幻", 7);
    var romance = movie(3, "爱情", 9);
    var service = new RecommendationService();
    var candidates = List.of(seen, science, romance);
    assertEquals(
        3, service.rank(candidates, List.of(), List.of(), List.of()).getFirst().movie().id());
    var result =
        service.rank(
            candidates,
            List.of(seen),
            List.of(new LibraryMapper.Entry(1, "WATCHED")),
            List.of(new LibraryMapper.Review(1, 5, "")));
    assertEquals(2, result.getFirst().movie().id());
    assertEquals("因为你偏爱科幻电影", result.getFirst().reason());
    assertTrue(result.stream().noneMatch(p -> p.movie().id() == 1));
  }

  @Test
  void lowRatingsDoNotBecomePositivePreferences() {
    var seen = movie(1, "科幻", 8);
    var service = new RecommendationService();
    var result =
        service.rank(
            List.of(movie(2, "科幻", 8), movie(3, "爱情", 8)),
            List.of(seen),
            List.of(new LibraryMapper.Entry(1, "WATCHED")),
            List.of(new LibraryMapper.Review(1, 1, "")));
    assertEquals(3, result.getFirst().movie().id());
  }

  @Test
  void explicitColdStartPreferencesAndHiddenMoviesAreRespected() {
    var service = new RecommendationService();
    var result =
        service.rank(
            List.of(movie(1, "爱情", 9), movie(2, "动画", 8), movie(3, "动画", 9)),
            List.of(),
            List.of(),
            List.of(),
            List.of("动画"),
            Set.of(3L),
            List.of());
    assertEquals(2, result.getFirst().movie().id());
    assertTrue(result.getFirst().reason().contains("动画"));
    assertTrue(result.stream().noneMatch(p -> p.movie().id() == 3));
  }

  @Test
  void neutralRatingsDoNotBecomeLikesAndResultsAreDeterministic() {
    var service = new RecommendationService();
    var candidates =
        new ArrayList<>(List.of(movie(1, "科幻", 8), movie(2, "爱情", 8), movie(3, "科幻", 9)));
    var initial = service.rank(candidates, List.of(), List.of(), List.of());
    Collections.reverse(candidates);
    assertEquals(initial, service.rank(candidates, List.of(), List.of(), List.of()));
    var result =
        service.rank(
            candidates,
            List.of(movie(1, "科幻", 8)),
            List.of(),
            List.of(new LibraryMapper.Review(1, 3, "")));
    assertTrue(result.stream().noneMatch(p -> p.reason().startsWith("因为你偏爱")));
    assertTrue(result.stream().noneMatch(p -> p.movie().id() == 1));
  }

  @Test
  void similarUserEvidenceCanOutrankAnEquallyRatedCandidate() {
    var service = new RecommendationService();
    var saved = List.of(movie(1, "剧情", 8), movie(2, "剧情", 8), movie(3, "剧情", 8));
    var own =
        List.of(
            new LibraryMapper.Review(1, 5, ""),
            new LibraryMapper.Review(2, 3, ""),
            new LibraryMapper.Review(3, 1, ""));
    List<LibraryMapper.Signal> signals = new ArrayList<>();
    for (long user = 10; user < 20; user++) {
      signals.add(new LibraryMapper.Signal(user, 1, 5));
      signals.add(new LibraryMapper.Signal(user, 2, 3));
      signals.add(new LibraryMapper.Signal(user, 3, 1));
      signals.add(new LibraryMapper.Signal(user, 5, 5));
      signals.add(new LibraryMapper.Signal(user, 4, 1));
    }
    var candidates = List.of(movie(4, "剧情", 8), movie(5, "剧情", 8));
    assertEquals(4, service.rank(candidates, saved, List.of(), own).getFirst().movie().id());
    var result = service.rank(candidates, saved, List.of(), own, List.of(), Set.of(), signals);
    assertEquals(5, result.getFirst().movie().id());
    assertEquals("与你评分相近的影迷也喜欢", result.getFirst().reason());
  }

  @Test
  void resultsAreUniqueAndIncludeDifferentGenreCombinations() {
    var service = new RecommendationService();
    var candidates = new ArrayList<Movie>();
    for (int i = 1; i <= 30; i++) candidates.add(movie(i, i % 2 == 0 ? "动画,喜剧" : "剧情,悬疑", 8));
    var result = service.rank(candidates, List.of(), List.of(), List.of());
    assertEquals(12, result.size());
    assertEquals(12, result.stream().map(p -> p.movie().id()).distinct().count());
    assertTrue(result.stream().map(p -> p.movie().genres()).distinct().count() > 1);
  }

  @Test
  void directorClustersAreLimitedWhenAlternativesExist() {
    List<Movie> candidates = new ArrayList<>();
    for (int i = 1; i <= 15; i++) {
      var m = movie(i, "科幻", i <= 5 ? 9 : 8);
      candidates.add(
          new Movie(
              m.id(),
              m.title(),
              m.originalTitle(),
              m.releaseYear(),
              m.genres(),
              m.runtime(),
              m.rating(),
              m.ratingCount(),
              i <= 5 ? "同一位导演" : "导演" + i,
              m.country(),
              m.synopsis(),
              m.posterUrl(),
              m.backdropUrl(),
              false,
              "demo",
              null,
              null,
              null));
    }
    var result = new RecommendationService().rank(candidates, List.of(), List.of(), List.of());
    assertEquals(12, result.size());
    assertEquals(2, result.stream().filter(p -> p.movie().director().equals("同一位导演")).count());
    // A tiny single-director catalog still returns available movies rather than empty slots.
    assertEquals(
        5,
        new RecommendationService()
            .rank(candidates.subList(0, 5), List.of(), List.of(), List.of())
            .size());
  }

  Movie discoveryMovie(long id, String genres, int runtime, double rating, int votes) {
    return new Movie(
        id, "电影" + id, "Movie", 2026, genres, runtime, rating, votes, "导演" + id, "中国", "简介", "/p",
        "/b", false, "demo", null, null, null);
  }

  @Test
  void requestFiltersRespectRuntimeGenreAndEveryExclusionSource() {
    var candidates =
        List.of(
            discoveryMovie(1, "动画", 80, 9, 100), discoveryMovie(2, "动画", 0, 9, 100),
            discoveryMovie(3, "动画", 100, 9, 100), discoveryMovie(4, "剧情", 80, 9, 100),
            discoveryMovie(5, "动画", 80, 9, 100), discoveryMovie(6, "动画", 80, 9, 100),
            discoveryMovie(7, "动画", 80, 9, 100), discoveryMovie(8, "动画", 80, 9, 100));
    var result =
        new RecommendationService()
            .rank(
                candidates,
                List.of(),
                List.of(new LibraryMapper.Entry(5, "WATCHLIST")),
                List.of(new LibraryMapper.Review(6, 4, "")),
                List.of("剧情"),
                Set.of(7L),
                List.of(),
                new RecommendationService.Options(true, Set.of("动画"), 90, Set.of(8L)));
    assertEquals(List.of(1L), result.stream().map(p -> p.movie().id()).toList());
    assertEquals("因为你偏爱动画电影", result.getFirst().reason());
  }

  @Test
  void explorationHasBoundedSlotsAndOnlyMarksQualifiedTailMovies() {
    List<Movie> candidates = new ArrayList<>();
    for (int i = 1; i <= 50; i++)
      candidates.add(discoveryMovie(i, "剧情", 100, 8, i <= 39 ? 50 : 10000));
    var service = new RecommendationService();
    for (boolean explore : List.of(false, true)) {
      var result =
          service.rank(
              candidates,
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              Set.of(),
              List.of(),
              new RecommendationService.Options(explore, Set.of(), 0, Set.of()));
      assertEquals(12, result.size());
      assertEquals(
          explore ? 6 : 0, result.stream().filter(RecommendationService.Pick::exploration).count());
      assertTrue(
          result.stream()
              .filter(RecommendationService.Pick::exploration)
              .allMatch(p -> p.movie().ratingCount() == 50));
      assertEquals(12, result.stream().map(p -> p.movie().id()).distinct().count());
    }
  }

  @Test
  void explorationFallsBackWhenTailLacksRatingEvidence() {
    List<Movie> candidates = new ArrayList<>();
    for (int i = 1; i <= 50; i++)
      candidates.add(discoveryMovie(i, "剧情", 100, i <= 39 ? 6 : 8, i <= 39 ? 10 : 1000));
    var result =
        new RecommendationService()
            .rank(
                candidates,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                List.of(),
                new RecommendationService.Options(true, Set.of(), 0, Set.of()));
    assertEquals(12, result.size());
    assertTrue(result.stream().noneMatch(RecommendationService.Pick::exploration));
  }

  @Test
  void defaultKeepsPreviousRankingAfterExplorationEvaluation() {
    var candidates = new ArrayList<Movie>();
    for (int i = 1; i <= 60; i++)
      candidates.add(discoveryMovie(i, i % 2 == 0 ? "科幻,剧情" : "动画,喜剧", 100, 7 + (i % 3), 25 * i));
    var saved = candidates.subList(0, 3);
    var reviews =
        List.of(
            new LibraryMapper.Review(1, 5, ""),
            new LibraryMapper.Review(2, 1, ""),
            new LibraryMapper.Review(3, 3, ""));
    var previous = new PreviousHybridRecommender().rank(candidates, saved, List.of(), reviews);
    var current = new RecommendationService().rank(candidates, saved, List.of(), reviews);
    assertEquals(
        previous.stream().map(p -> p.movie().id()).toList(),
        current.stream().map(p -> p.movie().id()).toList());
    assertEquals(
        previous.stream().map(PreviousHybridRecommender.Pick::reason).toList(),
        current.stream().map(RecommendationService.Pick::reason).toList());
    assertTrue(current.stream().noneMatch(RecommendationService.Pick::exploration));
  }

  @Test
  void coldStartGenreDoesNotFavorWeakSingleGenreFilmsOverWellRatedMatches() {
    var candidates =
        List.of(
            discoveryMovie(1, "动画", 100, 5.6, 10000),
            discoveryMovie(2, "动画,剧情,科幻", 100, 9.3, 10000));
    var result =
        new RecommendationService()
            .rank(
                candidates,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                List.of(),
                new RecommendationService.Options(false, Set.of("动画"), 120, Set.of()));
    assertEquals(2, result.getFirst().movie().id());
  }
}

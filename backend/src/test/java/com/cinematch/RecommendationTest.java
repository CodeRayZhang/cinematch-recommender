package com.cinematch;

import static org.junit.jupiter.api.Assertions.*;

import com.cinematch.library.LibraryMapper;
import com.cinematch.movie.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationTest {
  Movie movie(long id, String genres, double rating) {
    return new Movie(
        id, "电影" + id, "Movie", 2026, genres, 100, rating, 1000, "导演", "中国", "简介", "/p", "/b",
        false);
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
}

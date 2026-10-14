package com.cinematch.movie;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Observational, authenticated first-party feedback. Never treats a returned card as viewed. */
@Service
public class RecommendationObservation {
  public static final String VERSION = "hybrid-v2.1";
  private final JdbcTemplate jdbc;

  public RecommendationObservation(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Metrics(
      String version,
      String mode,
      String segment,
      long users,
      long requests,
      long served,
      long exposed,
      long clicked,
      long saved,
      long rated,
      long highlyRated,
      Double clickRate,
      Double saveRate,
      Double ratingRate) {}

  public boolean enabled(long user) {
    return jdbc
        .query(
            "SELECT recommendation_tracking FROM user_profiles WHERE user_id=?",
            (r, n) -> r.getBoolean(1),
            user)
        .stream()
        .findFirst()
        .orElse(true);
  }

  @Transactional
  public void setting(long user, boolean enabled) {
    jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
    jdbc.update(
        "INSERT INTO user_profiles(user_id,recommendation_tracking) VALUES(?,?) ON DUPLICATE KEY"
            + " UPDATE recommendation_tracking=?",
        user,
        enabled,
        enabled);
  }

  @Transactional
  public void clear(long user) {
    jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
    jdbc.update("DELETE FROM recommendation_runs WHERE user_id=?", user);
  }

  @Transactional
  public String record(
      long user, String mode, int ratings, List<RecommendationService.Pick> picks) {
    return record(user, mode, ratings, picks, VERSION);
  }

  @Transactional
  public String record(
      long user, String mode, int ratings, List<RecommendationService.Pick> picks, String version) {
    if (picks.isEmpty()) return null;
    // Serialize per-user retention and opt-out with ordinary application transactions.
    jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
    if (!enabled(user)) return null;
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO recommendation_runs(id,user_id,algorithm_version,mode,segment)"
            + " VALUES(?,?,?,?,?)",
        id,
        user,
        version,
        mode,
        ratings == 0 ? "new" : ratings < 20 ? "sparse" : "active");
    for (int i = 0; i < picks.size(); i++)
      jdbc.update(
          "INSERT INTO recommendation_observations(run_id,movie_id,position) VALUES(?,?,?)",
          id,
          picks.get(i).movie().id(),
          i + 1);
    jdbc.update(
        "DELETE FROM recommendation_runs WHERE user_id=? AND created_at<?",
        user,
        Timestamp.from(Instant.now().minus(Duration.ofDays(90))));
    var old =
        jdbc.queryForList(
            "SELECT id FROM recommendation_runs WHERE user_id=? AND id<>? ORDER BY created_at"
                + " DESC,id DESC LIMIT 1000 OFFSET 199",
            String.class,
            user,
            id);
    old.forEach(run -> jdbc.update("DELETE FROM recommendation_runs WHERE id=?", run));
    return id;
  }

  @Transactional
  public void event(long user, String run, long movie, String event) {
    if (!enabled(user)) return;
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM recommendation_observations o JOIN recommendation_runs r ON"
                + " r.id=o.run_id WHERE r.user_id=? AND o.run_id=? AND o.movie_id=?",
            Long.class,
            user,
            run,
            movie)
        == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐记录不存在");
    String columns =
        event.equals("click")
            ? "exposed_at=COALESCE(exposed_at,CURRENT_TIMESTAMP),clicked_at=COALESCE(clicked_at,CURRENT_TIMESTAMP)"
            : "exposed_at=COALESCE(exposed_at,CURRENT_TIMESTAMP)";
    jdbc.update(
        "UPDATE recommendation_observations SET " + columns + " WHERE run_id=? AND movie_id=?",
        run,
        movie);
  }

  public void attribute(long user, long movie, String action, Integer stars) {
    if (!enabled(user)) return;
    var runs =
        jdbc.queryForList(
            "SELECT o.run_id FROM recommendation_observations o JOIN recommendation_runs r ON"
                + " r.id=o.run_id WHERE r.user_id=? AND o.movie_id=? AND o.exposed_at>=? ORDER BY"
                + " o.exposed_at DESC,o.run_id DESC LIMIT 1",
            String.class,
            user,
            movie,
            Timestamp.from(Instant.now().minus(Duration.ofDays(7))));
    if (runs.isEmpty()) return;
    if (action.equals("saved"))
      jdbc.update(
          "UPDATE recommendation_observations SET saved_at=COALESCE(saved_at,CURRENT_TIMESTAMP)"
              + " WHERE run_id=? AND movie_id=?",
          runs.getFirst(),
          movie);
    else
      jdbc.update(
          "UPDATE recommendation_observations SET"
              + " rated_at=COALESCE(rated_at,CURRENT_TIMESTAMP),rating_stars=? WHERE run_id=? AND"
              + " movie_id=?",
          stars,
          runs.getFirst(),
          movie);
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 86400000, initialDelay = 60000)
  public void prune() {
    jdbc.update(
        "DELETE FROM recommendation_runs WHERE created_at<?",
        Timestamp.from(Instant.now().minus(Duration.ofDays(90))));
  }

  public List<Metrics> metrics(int days) {
    return jdbc.query(
        "SELECT r.algorithm_version,r.mode,r.segment,COUNT(DISTINCT r.user_id) AS"
            + " users,COUNT(DISTINCT r.id) AS requests,COUNT(*) AS served,SUM(CASE WHEN"
            + " o.exposed_at IS NOT NULL THEN 1 ELSE 0 END) AS exposed,SUM(CASE WHEN o.clicked_at"
            + " IS NOT NULL THEN 1 ELSE 0 END) AS clicked,SUM(CASE WHEN o.saved_at IS NOT NULL THEN"
            + " 1 ELSE 0 END) AS saved,SUM(CASE WHEN o.rated_at IS NOT NULL THEN 1 ELSE 0 END) AS"
            + " rated,SUM(CASE WHEN o.rating_stars>=4 THEN 1 ELSE 0 END) AS highly_rated FROM"
            + " recommendation_runs r JOIN recommendation_observations o ON r.id=o.run_id WHERE"
            + " r.created_at>=? GROUP BY r.algorithm_version,r.mode,r.segment ORDER BY"
            + " r.algorithm_version,r.mode,r.segment",
        (r, n) -> {
          long exposed = r.getLong("exposed"),
              clicked = r.getLong("clicked"),
              saved = r.getLong("saved"),
              rated = r.getLong("rated");
          return new Metrics(
              r.getString("algorithm_version"),
              r.getString("mode"),
              r.getString("segment"),
              r.getLong("users"),
              r.getLong("requests"),
              r.getLong("served"),
              exposed,
              clicked,
              saved,
              rated,
              r.getLong("highly_rated"),
              exposed == 0 ? null : clicked / (double) exposed,
              exposed == 0 ? null : saved / (double) exposed,
              exposed == 0 ? null : rated / (double) exposed);
        },
        Timestamp.from(Instant.now().minus(Duration.ofDays(days))));
  }
}

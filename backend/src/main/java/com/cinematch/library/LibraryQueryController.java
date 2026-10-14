package com.cinematch.library;

import com.cinematch.auth.AccountMapper;
import com.cinematch.movie.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
public class LibraryQueryController {
  private final JdbcTemplate jdbc;
  private final AccountMapper accounts;
  private final LibraryMapper library;

  public LibraryQueryController(JdbcTemplate jdbc, AccountMapper accounts, LibraryMapper library) {
    this.jdbc = jdbc;
    this.accounts = accounts;
    this.library = library;
  }

  public record Row(Movie movie, String status, Integer stars, String body) {}

  public record Page(List<Row> items, long total, int page, int size) {}

  public record Genre(String name, long count) {}

  public record Summary(
      long watching, long watched, long rated, long minutes, Double average, List<Genre> genres) {}

  private long user(Principal p) {
    return accounts.findByUsername(p.getName()).id();
  }

  @GetMapping("/library/state")
  public Map<String, Object> state(Principal p) {
    long id = user(p);
    // Only identifiers and stars needed by cards; full texts and movie metadata are paged.
    return Map.of(
        "entries",
        library.entries(id),
        "reviews",
        jdbc.query(
            "SELECT movie_id,stars FROM reviews WHERE user_id=? ORDER BY updated_at DESC,movie_id"
                + " DESC",
            (r, n) -> new LibraryMapper.Review(r.getLong(1), r.getInt(2), ""),
            id),
        "movies",
        List.of());
  }

  @GetMapping("/reviews/{movie}")
  public LibraryMapper.Review review(Principal p, @PathVariable long movie) {
    return jdbc
        .query(
            "SELECT movie_id,stars,body FROM reviews WHERE user_id=? AND movie_id=?",
            (r, n) -> new LibraryMapper.Review(r.getLong(1), r.getInt(2), r.getString(3)),
            user(p),
            movie)
        .stream()
        .findFirst()
        .orElse(new LibraryMapper.Review(movie, 0, ""));
  }

  @GetMapping("/library/page")
  public Page page(
      Principal p,
      @RequestParam(defaultValue = "ALL") String tab,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "recent") String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "24") int size) {
    if (!Set.of("ALL", "WATCHLIST", "WATCHED", "RATED").contains(tab)
        || !Set.of("recent", "rating", "title").contains(sort)
        || q.length() > 100
        || page < 1
        || page > 10000
        || size < 1
        || size > 48) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "片单筛选参数无效");
    long id = user(p);
    String condition =
        switch (tab) {
          case "RATED" -> "r.movie_id IS NOT NULL";
          case "WATCHLIST" -> "l.status='WATCHLIST'";
          case "WATCHED" -> "l.status='WATCHED'";
          default -> "l.movie_id IS NOT NULL";
        };
    String base =
        " FROM movies m LEFT JOIN library_entries l ON m.id=l.movie_id AND l.user_id=? LEFT JOIN"
            + " reviews r ON m.id=r.movie_id AND r.user_id=? WHERE "
            + condition
            + " AND (?='' OR LOWER(m.title) LIKE CONCAT('%',LOWER(?),'%') OR"
            + " LOWER(m.original_title) LIKE CONCAT('%',LOWER(?),'%') OR LOWER(m.director) LIKE"
            + " CONCAT('%',LOWER(?),'%'))";
    Object[] params = {id, id, q.strip(), q.strip(), q.strip(), q.strip()};
    long total = jdbc.queryForObject("SELECT COUNT(*)" + base, Long.class, params);
    String order =
        switch (sort) {
          case "rating" -> "COALESCE(r.stars,0) DESC,m.id ASC";
          case "title" -> "m.title ASC,m.id ASC";
          default -> (tab.equals("RATED") ? "r.updated_at" : "l.updated_at") + " DESC,m.id DESC";
        };
    List<Object> args = new ArrayList<>(Arrays.asList(params));
    args.add(size);
    args.add((page - 1) * size);
    var rows =
        jdbc.query(
            "SELECT m.*,l.status,r.stars,r.body"
                + base
                + " ORDER BY "
                + order
                + " LIMIT ? OFFSET ?",
            (r, n) ->
                new Row(
                    MovieRows.read(r),
                    r.getString("status"),
                    r.getObject("stars") == null ? null : r.getInt("stars"),
                    r.getString("body")),
            args.toArray());
    return new Page(rows, total, page, size);
  }

  @GetMapping("/library/summary")
  public Summary summary(Principal p) {
    long id = user(p);
    long watching =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM library_entries WHERE user_id=? AND status='WATCHLIST'",
            Long.class,
            id);
    var watched =
        jdbc.queryForList(
            "SELECT m.runtime,m.genres FROM movies m JOIN library_entries l ON l.movie_id=m.id"
                + " WHERE l.user_id=? AND l.status='WATCHED'",
            id);
    long minutes = 0;
    Map<String, Long> genres = new HashMap<>();
    for (var row : watched) {
      minutes += ((Number) row.get("runtime")).longValue();
      for (String genre : ((String) row.get("genres")).split(","))
        genres.merge(genre, 1L, Long::sum);
    }
    long rated =
        jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE user_id=?", Long.class, id);
    Double average =
        jdbc.queryForObject(
            "SELECT AVG(CAST(stars AS DOUBLE)) FROM reviews WHERE user_id=?", Double.class, id);
    return new Summary(
        watching,
        watched.size(),
        rated,
        minutes,
        average,
        genres.entrySet().stream()
            .sorted(
                Map.Entry.<String, Long>comparingByValue()
                    .reversed()
                    .thenComparing(Map.Entry.comparingByKey()))
            .limit(5)
            .map(e -> new Genre(e.getKey(), e.getValue()))
            .toList());
  }
}

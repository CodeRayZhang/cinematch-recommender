package com.cinematch.collection;

import com.cinematch.movie.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CollectionService {
  private final JdbcTemplate jdbc;

  public CollectionService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Shelf(
      String id,
      String name,
      String description,
      Long coverMovieId,
      String coverUrl,
      long movieCount,
      String updatedAt) {}

  public record Item(Movie movie, String note, int position) {}

  public record Page(Shelf collection, List<Item> items, long total, int page, int size) {}

  public record Export(String format, String exportedAt, Shelf collection, List<Item> items) {}

  private static final String SHELVES =
      "SELECT c.*, (SELECT COUNT(*) FROM collection_movies cm WHERE cm.collection_id=c.id) AS"
          + " movie_count, COALESCE((SELECT poster_url FROM movies WHERE id=c.cover_movie_id),"
          + " (SELECT m.poster_url FROM collection_movies cm JOIN movies m ON m.id=cm.movie_id"
          + " WHERE cm.collection_id=c.id ORDER BY cm.position,cm.movie_id LIMIT 1),"
          + " '/images/placeholder.svg') AS cover_url FROM movie_collections c";

  public List<Shelf> list(long user) {
    return jdbc.query(
        SHELVES + " WHERE c.user_id=? ORDER BY c.updated_at DESC,c.id",
        (r, n) ->
            new Shelf(
                r.getString("id"),
                r.getString("name"),
                r.getString("description"),
                r.getObject("cover_movie_id") == null ? null : r.getLong("cover_movie_id"),
                r.getString("cover_url"),
                r.getLong("movie_count"),
                r.getTimestamp("updated_at").toInstant().toString()),
        user);
  }

  public Shelf require(long user, String id) {
    return list(user).stream()
        .filter(c -> c.id().equals(id))
        .findFirst()
        .orElseThrow(() -> missing());
  }

  private void lock(long user, String id) {
    if (jdbc.queryForList(
            "SELECT id FROM movie_collections WHERE id=? AND user_id=? FOR UPDATE", id, user)
        .isEmpty()) throw missing();
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "片单不存在");
  }

  private void touch(String id) {
    jdbc.update("UPDATE movie_collections SET updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
  }

  @Transactional
  public Shelf create(long user, String name, String description) {
    jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user);
    if (list(user).size() >= 50)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多创建 50 个自定义片单");
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO movie_collections(id,user_id,name,description) VALUES(?,?,?,?)",
        id,
        user,
        name.strip(),
        description.strip());
    return require(user, id);
  }

  @Transactional
  public Shelf update(long user, String id, String name, String description, Long cover) {
    lock(user, id);
    if (cover != null
        && jdbc.queryForObject(
                "SELECT COUNT(*) FROM collection_movies WHERE collection_id=? AND movie_id=?",
                Long.class,
                id,
                cover)
            == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "封面请选择片单中的电影");
    jdbc.update(
        "UPDATE movie_collections SET"
            + " name=?,description=?,cover_movie_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        name.strip(),
        description.strip(),
        cover,
        id);
    return require(user, id);
  }

  @Transactional
  public void delete(long user, String id) {
    lock(user, id);
    jdbc.update("DELETE FROM movie_collections WHERE id=?", id);
  }

  public Page page(long user, String id, int page, int size) {
    Shelf shelf = require(user, id);
    return new Page(shelf, items(id, size, (page - 1) * size), shelf.movieCount(), page, size);
  }

  private List<Item> items(String id, int size, int offset) {
    return jdbc.query(
        "SELECT m.*,cm.note,cm.position FROM collection_movies cm JOIN movies m ON m.id=cm.movie_id"
            + " WHERE cm.collection_id=? ORDER BY cm.position,cm.movie_id LIMIT ? OFFSET ?",
        (r, n) -> new Item(MovieRows.read(r), r.getString("note"), r.getInt("position")),
        id,
        size,
        offset);
  }

  @Transactional(readOnly = true)
  public Export export(long user, String id) {
    Shelf shelf = require(user, id);
    return new Export(
        "cinematch.collection.v1", Instant.now().toString(), shelf, items(id, 1000, 0));
  }

  @Transactional
  public void add(long user, String id, List<Long> movies) {
    lock(user, id);
    Set<Long> added = new LinkedHashSet<>(movies);
    List<Long> existing =
        jdbc.queryForList(
            "SELECT movie_id FROM collection_movies WHERE collection_id=?", Long.class, id);
    added.removeAll(existing);
    if (existing.size() + added.size() > 1000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每个自定义片单最多 1000 部电影");
    for (long movie : added)
      if (jdbc.queryForObject("SELECT COUNT(*) FROM movies WHERE id=?", Long.class, movie) == 0)
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "电影不存在，未修改片单");
    int position =
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(position),0) FROM collection_movies WHERE collection_id=?",
            Integer.class,
            id);
    for (long movie : added)
      jdbc.update(
          "INSERT INTO collection_movies(collection_id,movie_id,position) VALUES(?,?,?)",
          id,
          movie,
          ++position);
    touch(id);
  }

  @Transactional
  public void remove(long user, String id, long movie) {
    lock(user, id);
    jdbc.update(
        "UPDATE movie_collections SET cover_movie_id=NULL WHERE id=? AND cover_movie_id=?",
        id,
        movie);
    jdbc.update("DELETE FROM collection_movies WHERE collection_id=? AND movie_id=?", id, movie);
    touch(id);
  }

  @Transactional
  public void note(long user, String id, long movie, String note) {
    lock(user, id);
    if (jdbc.update(
            "UPDATE collection_movies SET note=? WHERE collection_id=? AND movie_id=?",
            note.strip(),
            id,
            movie)
        == 0) throw missing();
    touch(id);
  }

  @Transactional
  public void move(long user, String id, long movie, String direction) {
    lock(user, id);
    List<Long> ordered =
        jdbc.queryForList(
            "SELECT movie_id FROM collection_movies WHERE collection_id=? ORDER BY"
                + " position,movie_id",
            Long.class,
            id);
    int index = ordered.indexOf(movie);
    if (index < 0) throw missing();
    int next = index + (direction.equals("up") ? -1 : 1);
    if (next < 0 || next >= ordered.size()) return;
    Collections.swap(ordered, index, next);
    for (int i = 0; i < ordered.size(); i++)
      jdbc.update(
          "UPDATE collection_movies SET position=? WHERE collection_id=? AND movie_id=?",
          i + 1,
          id,
          ordered.get(i));
    touch(id);
  }
}

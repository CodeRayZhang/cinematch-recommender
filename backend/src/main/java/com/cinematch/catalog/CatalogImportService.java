package com.cinematch.catalog;

import com.cinematch.admin.AdminController;
import jakarta.validation.Validator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Explicit local-file import. No public HTTP endpoint or new privileged account is needed. */
@Service
public class CatalogImportService {
  private final JdbcTemplate jdbc;
  private final Validator validator;
  private final org.springframework.context.ApplicationEventPublisher events;

  public CatalogImportService(
      JdbcTemplate jdbc,
      Validator validator,
      org.springframework.context.ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.validator = validator;
    this.events = events;
  }

  public record Entry(
      String sourceId, String sourceUrl, String snapshot, AdminController.Input movie) {}

  public record Result(int created, int updated, int skipped) {}

  @Transactional
  public Result importFile(Path path) throws java.io.IOException {
    if (!Files.isRegularFile(path) || Files.size(path) > 30_000_000)
      throw new IllegalArgumentException("目录文件不存在或超过 30 MB");
    Entry[] rows = JsonMapper.builder().build().readValue(Files.readString(path), Entry[].class);
    if (rows == null || rows.length == 0 || rows.length > 10000)
      throw new IllegalArgumentException("目录条数须在 1–10000 之间");
    Set<String> ids = new HashSet<>();
    for (Entry row : rows) {
      if (row == null
          || row.movie() == null
          || row.sourceId() == null
          || !row.sourceId().matches("[1-9][0-9]{0,19}")
          || !("https://movie.douban.com/subject/" + row.sourceId() + "/").equals(row.sourceUrl())
          || row.snapshot() == null
          || !row.snapshot().matches("[0-9]{4}(-[0-9]{2}-[0-9]{2})?")
          || !ids.add(row.sourceId())
          || !validator.validate(row.movie()).isEmpty())
        throw new IllegalArgumentException("目录字段无效或来源 ID 重复；没有写入数据");
    }
    int created = 0, updated = 0, skipped = 0;
    for (Entry row : rows) {
      var movie = row.movie();
      var existing =
          jdbc.queryForList("SELECT * FROM movies WHERE source_url = ?", row.sourceUrl());
      if (existing.isEmpty()) {
        existing =
            jdbc.queryForList(
                "SELECT * FROM movies WHERE title = ? AND release_year = ? AND source_url IS NULL",
                movie.title(),
                movie.releaseYear());
      }
      if (existing.size() > 1) throw new IllegalArgumentException("电影匹配存在歧义：" + movie.title());
      if (!existing.isEmpty() && "manual".equals(existing.getFirst().get("data_source"))) {
        skipped++;
        continue;
      }
      String originalTitle = movie.originalTitle(),
          poster = movie.posterUrl(),
          backdrop = movie.backdropUrl();
      boolean featured = false;
      if (!existing.isEmpty()) {
        var old = existing.getFirst();
        featured =
            Boolean.TRUE.equals(old.get("featured"))
                || Integer.valueOf(1).equals(old.get("featured"));
        originalTitle = (String) old.get("original_title");
        if ("demo".equals(old.get("data_source"))) {
          poster = (String) old.get("poster_url");
          backdrop = (String) old.get("backdrop_url");
        }
      }
      List<Object> values =
          new ArrayList<>(
              Arrays.asList(
                  movie.title(),
                  originalTitle,
                  movie.releaseYear(),
                  movie.genres(),
                  movie.runtime(),
                  movie.rating(),
                  movie.ratingCount(),
                  movie.director(),
                  movie.country(),
                  movie.synopsis(),
                  poster,
                  backdrop,
                  featured,
                  "douban",
                  row.sourceId(),
                  row.sourceUrl(),
                  row.snapshot()));
      if (existing.isEmpty()) {
        jdbc.update(
            "INSERT INTO"
                + " movies(title,original_title,release_year,genres,runtime,rating,rating_count,director,country,synopsis,poster_url,backdrop_url,featured,data_source,source_id,source_url,source_snapshot)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            values.toArray());
        created++;
      } else {
        values.add(existing.getFirst().get("id"));
        jdbc.update(
            "UPDATE movies SET"
                + " title=?,original_title=?,release_year=?,genres=?,runtime=?,rating=?,rating_count=?,director=?,country=?,synopsis=?,poster_url=?,backdrop_url=?,featured=?,data_source=?,source_id=?,source_url=?,source_snapshot=?"
                + " WHERE id=?",
            values.toArray());
        updated++;
      }
    }
    events.publishEvent(new com.cinematch.movie.CatalogCache.Changed());
    return new Result(created, updated, skipped);
  }
}

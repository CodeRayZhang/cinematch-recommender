package com.cinematch.recommender;

import com.cinematch.movie.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TrainingData {
  public record Mapping(
      long movieId,
      long itemId,
      String title,
      int releaseYear,
      String sourceUrl,
      String imdbId,
      String evidence) {
    public boolean matches(Movie movie) {
      return movie.id() == movieId
          && movie.releaseYear() == releaseYear
          && (sourceUrl != null && !sourceUrl.isBlank()
              ? sourceUrl.equals(movie.sourceUrl())
              : title.equals(movie.title()));
    }
  }

  public record Dataset(
      String name,
      int ratings,
      int users,
      int items,
      int mappedItems,
      boolean available,
      String message) {}

  public record Input(
      List<AlsModel.Rating> ratings, List<Mapping> mappings, Map<String, Object> summary) {}

  private final Path root;
  private final JdbcTemplate jdbc;
  private final JsonMapper json = JsonMapper.builder().build();

  public TrainingData(
      @Value("${app.recommender.data-directory:./.data/recommender}") String directory,
      JdbcTemplate jdbc) {
    root = Path.of(directory).toAbsolutePath().normalize();
    this.jdbc = jdbc;
  }

  private Path dataset(String name) {
    if (!name.matches("[a-z0-9][a-z0-9-]{0,59}") || name.equals("local"))
      throw new IllegalArgumentException("数据集名称无效");
    return root.resolve("datasets").resolve(name);
  }

  private Path checked(Path dir, String file, long limit) throws IOException {
    Path path = dir.resolve(file);
    if (!Files.isRegularFile(path)
        || !path.toRealPath().startsWith(root.toRealPath())
        || Files.size(path) > limit) throw new IllegalArgumentException("训练文件缺失、越界或超过大小上限：" + file);
    return path;
  }

  public List<Dataset> datasets() {
    long local = jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Long.class);
    long users = jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM reviews", Long.class);
    long items = jdbc.queryForObject("SELECT COUNT(DISTINCT movie_id) FROM reviews", Long.class);
    List<Dataset> out = new ArrayList<>();
    out.add(
        new Dataset(
            "local",
            (int) local,
            (int) users,
            (int) items,
            (int) items,
            local >= 20 && users >= 5 && items >= 5,
            "本站评分；至少 5 位用户、5 部电影、20 条评分"));
    Path directory = root.resolve("datasets");
    if (Files.isDirectory(directory))
      try (var files = Files.list(directory)) {
        for (Path p : files.sorted().limit(20).toList())
          if (Files.isDirectory(p)
              && p.getFileName().toString().matches("[a-z0-9][a-z0-9-]{0,59}")) {
            try {
              var m = json.readTree(checked(p, "manifest.json", 1_000_000).toFile());
              out.add(
                  new Dataset(
                      p.getFileName().toString(),
                      m.path("ratings").asInt(),
                      m.path("users").asInt(),
                      m.path("items").asInt(),
                      m.path("mappedItems").asInt(),
                      true,
                      "训练时重新校验 SHA-256、电影身份与实际行数"));
            } catch (Exception e) {
              out.add(new Dataset(p.getFileName().toString(), 0, 0, 0, 0, false, "清单不可读取，请重新准备数据"));
            }
          }
      } catch (IOException e) {
        throw new IllegalStateException("无法列出训练数据", e);
      }
    return out;
  }

  public Input load(String name, String run) throws Exception {
    Map<Long, Movie> movies = new HashMap<>();
    jdbc.query("SELECT * FROM movies", (r, n) -> MovieRows.read(r))
        .forEach(m -> movies.put(m.id(), m));
    List<AlsModel.Rating> ratings = new ArrayList<>();
    List<Mapping> mappings = new ArrayList<>();
    Map<String, Object> summary = new LinkedHashMap<>();
    if (name.equals("local")) {
      ratings.addAll(
          jdbc.query(
              "SELECT user_id,movie_id,stars,updated_at FROM reviews ORDER BY user_id,movie_id",
              (r, n) ->
                  new AlsModel.Rating(
                      r.getLong(1),
                      r.getLong(2),
                      r.getDouble(3),
                      r.getTimestamp(4).toInstant().getEpochSecond())));
      ratings.stream()
          .map(AlsModel.Rating::item)
          .distinct()
          .sorted()
          .forEach(
              id -> {
                var m = movies.get(id);
                mappings.add(
                    new Mapping(
                        id, id, m.title(), m.releaseYear(), m.sourceUrl(), "", "site-movie-id"));
              });
    } else {
      Path dir = dataset(name);
      var manifest = json.readTree(checked(dir, "manifest.json", 1_000_000).toFile());
      if (!manifest.path("format").asText().equals("cinematch.ratings.v1"))
        throw new IllegalArgumentException("训练清单版本不支持");
      Path ratingFile = checked(dir, "ratings.csv", 128_000_000),
          mappingFile = checked(dir, "mappings.json", 10_000_000);
      for (Path file : List.of(ratingFile, mappingFile)) {
        String digest = sha(file);
        if (!digest.equals(manifest.path("sha256").path(file.getFileName().toString()).asText()))
          throw new IllegalArgumentException("训练数据校验和不一致");
        summary.put(file.getFileName() + "Sha256", digest);
      }
      try (var reader = Files.newBufferedReader(ratingFile)) {
        if (!"userId,movieId,rating,timestamp".equals(reader.readLine()))
          throw new IllegalArgumentException("评分 CSV 表头错误");
        String line;
        while ((line = reader.readLine()) != null) {
          if (ratings.size() >= 2_000_000 || line.length() > 200)
            throw new IllegalArgumentException("训练数据超过大小上限");
          String[] p = line.split(",", -1);
          if (p.length != 4) throw new IllegalArgumentException("评分 CSV 字段数错误");
          long user = Long.parseLong(p[0]),
              item = Long.parseLong(p[1]),
              time = Long.parseLong(p[3]);
          double value = Double.parseDouble(p[2]);
          if (user <= 0
              || item <= 0
              || time < 0
              || !Double.isFinite(value)
              || value < .5
              || value > 5) throw new IllegalArgumentException("外部评分字段无效");
          ratings.add(new AlsModel.Rating(user, item, value, time));
        }
      }
      Set<Long> covered = new HashSet<>();
      ratings.forEach(r -> covered.add(r.item()));
      Set<Long> localIds = new HashSet<>(), externalIds = new HashSet<>();
      int stale = 0;
      for (Mapping map : json.readValue(mappingFile.toFile(), Mapping[].class)) {
        if (map.movieId() <= 0
            || map.itemId() <= 0
            || map.title() == null
            || map.evidence() == null
            || map.evidence().isBlank()
            || map.imdbId() == null
            || !map.imdbId().matches("tt[0-9]{7,10}")
            || !localIds.add(map.movieId())
            || !externalIds.add(map.itemId())) throw new IllegalArgumentException("电影映射无效或重复");
        Movie movie = movies.get(map.movieId());
        if (movie != null && map.matches(movie) && covered.contains(map.itemId()))
          mappings.add(map);
        else stale++;
      }
      for (Path file : List.of(ratingFile, mappingFile))
        if (!sha(file).equals(summary.get(file.getFileName() + "Sha256")))
          throw new IllegalArgumentException("读取期间训练源已改变，请重试");
      summary.put("skippedMappings", stale);
      if (ratings.size() != manifest.path("ratings").asInt()
          || ratings.stream().map(AlsModel.Rating::user).distinct().count()
              != manifest.path("users").asInt()
          || covered.size() != manifest.path("items").asInt())
        throw new IllegalArgumentException("清单统计与评分文件不一致");
      summary.put("externalRatings", ratings.size());
      // External user IDs and site users occupy separate namespaces, even when numerically equal.
      Map<Long, Long> items = new HashMap<>();
      mappings.forEach(m -> items.put(m.movieId(), m.itemId()));
      var own =
          jdbc.query(
              "SELECT user_id,movie_id,stars,updated_at FROM reviews ORDER BY user_id,movie_id",
              (r, n) ->
                  new AlsModel.Rating(
                      -r.getLong(1),
                      r.getLong(2),
                      r.getDouble(3),
                      r.getTimestamp(4).toInstant().getEpochSecond()));
      int appended = 0;
      for (var row : own)
        if (items.containsKey(row.item())) {
          ratings.add(
              new AlsModel.Rating(row.user(), items.get(row.item()), row.value(), row.time()));
          appended++;
        }
      summary.put("siteRatings", appended);
    }
    int users = (int) ratings.stream().map(AlsModel.Rating::user).distinct().count();
    int items = (int) ratings.stream().map(AlsModel.Rating::item).distinct().count();
    if (ratings.size() < 20 || users < 5 || items < 5 || mappings.size() < 3)
      throw new IllegalArgumentException("至少需要 5 位用户、5 部电影、20 条评分与 3 部可用映射电影");
    if (ratings.size() > 2_000_000 || users > 100000 || items > 10000)
      throw new IllegalArgumentException("数据超过单机训练上限");
    ratings.sort(
        Comparator.comparingLong(AlsModel.Rating::user).thenComparingLong(AlsModel.Rating::item));
    Path snapshot = root.resolve("runs").resolve(run);
    Files.createDirectories(snapshot);
    try (var writer =
        Files.newBufferedWriter(snapshot.resolve("ratings.csv"), StandardOpenOption.CREATE_NEW)) {
      writer.write("userId,movieId,rating,timestamp\n");
      for (var row : ratings)
        writer.write(row.user() + "," + row.item() + "," + row.value() + "," + row.time() + "\n");
    }
    Files.writeString(
        snapshot.resolve("mappings.json"),
        json.writeValueAsString(mappings),
        StandardOpenOption.CREATE_NEW);
    summary.put("ratings", ratings.size());
    summary.put("users", users);
    summary.put("items", items);
    summary.put("mappedItems", mappings.size());
    summary.put("snapshotSha256", sha(snapshot.resolve("ratings.csv")));
    summary.put("dataset", name);
    Files.writeString(
        snapshot.resolve("manifest.json"),
        json.writeValueAsString(summary),
        StandardOpenOption.CREATE_NEW);
    return new Input(List.copyOf(ratings), List.copyOf(mappings), summary);
  }

  private static String sha(Path file) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    try (var in = Files.newInputStream(file)) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) > 0) md.update(b, 0, n);
    }
    return HexFormat.of().formatHex(md.digest());
  }
}

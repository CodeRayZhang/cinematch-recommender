package com.cinematch;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cinematch.library.LibraryMapper;
import com.cinematch.movie.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Optional real-data evaluation; never connects to the application's database. */
class RecommendationEvaluationTest {
  record Rating(long user, long movie, int stars, long time) {}

  record Metadata(String title, int year, String genres) {}

  record Metrics(
      double recall,
      double precision,
      double ndcg,
      double hitRate,
      double diversity,
      double tailExposure,
      double millis) {}

  @Test
  void compareOnChronologicalMovieLensHoldout() throws Exception {
    String input = System.getProperty("recommendation.evaluation.data", "");
    assumeTrue(
        !input.isBlank(),
        "Set recommendation.evaluation.data to an existing MovieLens 1M directory");
    Path root = Path.of(input);
    boolean discovery = Boolean.getBoolean("recommendation.evaluation.discovery");
    Map<Long, Metadata> metadata = new TreeMap<>();
    for (String line :
        Files.readAllLines(root.resolve("movies.dat"), StandardCharsets.ISO_8859_1)) {
      String[] parts = line.contains("::") ? line.split("::", 3) : line.split(",", 2);
      long id = Long.parseLong(parts[0]);
      String title, genres;
      if (parts.length == 3) {
        title = parts[1];
        genres = parts[2];
      } else {
        int last = parts[1].lastIndexOf(',');
        title = parts[1].substring(0, last);
        genres = parts[1].substring(last + 1);
      }
      var match = java.util.regex.Pattern.compile("\\((\\d{4})\\) *$").matcher(title);
      int year = match.find() ? Integer.parseInt(match.group(1)) : 0;
      metadata.put(id, new Metadata(title, year, genres.replace('|', ',')));
    }
    List<Rating> ratings = new ArrayList<>();
    for (String line : Files.readAllLines(root.resolve("ratings.dat"))) {
      String[] p = line.split(line.contains("::") ? "::" : ",");
      ratings.add(
          new Rating(
              Long.parseLong(p[0]),
              Long.parseLong(p[1]),
              Integer.parseInt(p[2]),
              Long.parseLong(p[3])));
    }
    ratings.sort(
        Comparator.comparingLong(Rating::time)
            .thenComparingLong(Rating::user)
            .thenComparingLong(Rating::movie));
    long cutoff = ratings.get((int) (ratings.size() * .8) - 1).time;
    Map<Long, List<Rating>> train = new TreeMap<>(), test = new TreeMap<>();
    Map<Long, int[]> aggregate = new HashMap<>();
    for (Rating r : ratings) {
      (r.time <= cutoff ? train : test).computeIfAbsent(r.user, id -> new ArrayList<>()).add(r);
      if (r.time <= cutoff) {
        int[] a = aggregate.computeIfAbsent(r.movie, id -> new int[2]);
        a[0] += r.stars;
        a[1]++;
      }
    }
    List<Movie> catalog = new ArrayList<>();
    Map<Long, Movie> byId = new HashMap<>();
    for (var entry : metadata.entrySet()) {
      var a = aggregate.get(entry.getKey());
      if (a == null) continue;
      var m = entry.getValue();
      var movie =
          new Movie(
              entry.getKey(),
              m.title,
              m.title,
              m.year,
              m.genres,
              0,
              2.0 * a[0] / a[1],
              a[1],
              "",
              "",
              "",
              "",
              "",
              false,
              "manual",
              null,
              null,
              null);
      catalog.add(movie);
      byId.put(movie.id(), movie);
    }
    List<Long> eligible = new ArrayList<>();
    for (long id : train.keySet()) {
      if (train.get(id).size() < 20) continue;
      long positives =
          test.getOrDefault(id, List.of()).stream()
              .filter(r -> r.stars >= 4 && byId.containsKey(r.movie))
              .count();
      if (positives >= 5) eligible.add(id);
    }
    Collections.shuffle(eligible, new Random(20261013));
    Set<Long> previousUsers = new HashSet<>(eligible.subList(0, Math.min(100, eligible.size())));
    List<Long> pool = new ArrayList<>(eligible);
    if (discovery) {
      pool.removeAll(previousUsers);
      Collections.shuffle(pool, new Random(20261014));
    }
    List<Long> users = pool.subList(0, Math.min(100, pool.size()));
    if (discovery) assertTrue(Collections.disjoint(users, previousUsers));
    int[] popularity = catalog.stream().mapToInt(Movie::ratingCount).sorted().toArray();
    int headThreshold = popularity[(int) (popularity.length * .8)];
    assertTrue(users.size() >= 30, "Insufficient eligible holdout users");
    var baseline = new BaselineRecommender();
    var previous = new PreviousHybridRecommender();
    var upgraded = new RecommendationService();
    Map<String, List<Metrics>> results = new LinkedHashMap<>();
    Map<String, Set<Long>> coverage = new LinkedHashMap<>();
    for (String name :
        (discovery
            ? List.of("previousHybrid", "balanced", "explore")
            : List.of("baseline", "content", "hybrid"))) {
      results.put(name, new ArrayList<>());
      coverage.put(name, new HashSet<>());
    }
    for (long user : users) {
      var own = train.get(user);
      Set<Long> seen = new HashSet<>();
      own.forEach(r -> seen.add(r.movie));
      var saved = own.stream().map(r -> byId.get(r.movie)).filter(Objects::nonNull).toList();
      var reviews = own.stream().map(r -> new LibraryMapper.Review(r.movie, r.stars, "")).toList();
      var entries = own.stream().map(r -> new LibraryMapper.Entry(r.movie, "WATCHED")).toList();
      Set<Long> relevant = new HashSet<>();
      test.get(user).stream()
          .filter(r -> r.stars >= 4 && byId.containsKey(r.movie) && !seen.contains(r.movie))
          .forEach(r -> relevant.add(r.movie));
      assertFalse(relevant.isEmpty());
      // Match the production retrieval limits: 200 peers, then 50,000 deterministic rows.
      List<long[]> overlaps = new ArrayList<>();
      for (var peer : train.entrySet())
        if (peer.getKey() != user) {
          long overlap = peer.getValue().stream().filter(r -> seen.contains(r.movie)).count();
          if (overlap >= 2) overlaps.add(new long[] {peer.getKey(), overlap});
        }
      overlaps.sort(
          Comparator.<long[]>comparingLong(a -> a[1]).reversed().thenComparingLong(a -> a[0]));
      var neighborIds = overlaps.stream().limit(200).map(a -> a[0]).sorted().toList();
      List<LibraryMapper.Signal> signals = new ArrayList<>();
      for (long id : neighborIds)
        train.get(id).stream()
            .sorted(Comparator.comparingLong(Rating::movie))
            .forEach(
                r -> {
                  if (signals.size() < 50000)
                    signals.add(new LibraryMapper.Signal(id, r.movie, r.stars));
                });
      for (String model : results.keySet()) {
        long start = System.nanoTime();
        List<Movie> picked;
        if (model.equals("baseline"))
          picked =
              baseline.rank(catalog, saved, entries, reviews).stream()
                  .map(BaselineRecommender.Pick::movie)
                  .toList();
        else if (model.equals("balanced") || model.equals("explore"))
          picked =
              upgraded
                  .rank(
                      catalog,
                      saved,
                      entries,
                      reviews,
                      List.of(),
                      Set.of(),
                      signals,
                      new RecommendationService.Options(
                          model.equals("explore") ? 6 : 2, Set.of(), 0, Set.of()))
                  .stream()
                  .map(RecommendationService.Pick::movie)
                  .toList();
        else
          picked =
              previous
                  .rank(
                      catalog,
                      saved,
                      entries,
                      reviews,
                      List.of(),
                      Set.of(),
                      (model.equals("hybrid") || model.equals("previousHybrid"))
                          ? signals
                          : List.of())
                  .stream()
                  .map(PreviousHybridRecommender.Pick::movie)
                  .toList();
        double millis = (System.nanoTime() - start) / 1e6;
        assertEquals(12, picked.size());
        assertEquals(12, picked.stream().map(Movie::id).distinct().count());
        assertTrue(picked.stream().noneMatch(m -> seen.contains(m.id())));
        int hits = 0;
        double dcg = 0, ideal = 0, diversity = 0;
        for (int i = 0; i < picked.size(); i++) {
          if (relevant.contains(picked.get(i).id())) {
            hits++;
            dcg += 1 / (Math.log(i + 2) / Math.log(2));
          }
          if (i < relevant.size()) ideal += 1 / (Math.log(i + 2) / Math.log(2));
        }
        for (int i = 0; i < picked.size(); i++)
          for (int j = 0; j < i; j++) {
            Set<String> a = new HashSet<>(Arrays.asList(picked.get(i).genres().split(","))),
                b = new HashSet<>(Arrays.asList(picked.get(j).genres().split(",")));
            Set<String> union = new HashSet<>(a);
            union.addAll(b);
            a.retainAll(b);
            diversity += 1 - a.size() / (double) union.size();
          }
        results
            .get(model)
            .add(
                new Metrics(
                    hits / (double) relevant.size(),
                    hits / 12.0,
                    dcg / ideal,
                    hits > 0 ? 1 : 0,
                    diversity / 66,
                    picked.stream().filter(m -> m.ratingCount() < headThreshold).count() / 12.0,
                    millis));
        picked.forEach(m -> coverage.get(model).add(m.id()));
      }
    }
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("dataset", "MovieLens 1M (existing local copy; raw files not redistributed)");
    report.put("ratings", ratings.size());
    report.put("catalogMovies", catalog.size());
    report.put("cutoffUnix", cutoff);
    report.put("trainRatings", train.values().stream().mapToInt(List::size).sum());
    report.put("testRatings", test.values().stream().mapToInt(List::size).sum());
    report.put("eligibleUsers", eligible.size());
    report.put("evaluatedUsers", users.size());
    report.put("seed", discovery ? 20261014 : 20261013);
    if (discovery) {
      report.put("previousEvaluationUsersExcluded", previousUsers.size());
      report.put("freshEvaluationPool", pool.size());
      report.put("sampleUserIds", List.copyOf(users));
    }
    report.put("headPopularityThreshold", headThreshold);
    report.put(
        "tailDefinition", "Training rating count strictly below the full catalog 80th percentile");
    report.put("k", 12);
    if (discovery)
      report.put(
          "deploymentDecision",
          "Keep previousHybrid ordering as default; balanced is an unshipped two-slot experiment."
              + " Expose six-slot exploration only as an explicit opt-in.");
    report.put(
        "protocol",
        "Global chronological 80/20 split; tied cutoff timestamps stay in training; train-only"
            + " rating aggregates and neighbors; relevant >=4 stars; full train catalog minus seen;"
            + " sample users with >=20 train ratings and >=5 eligible future positives. No tuning"
            + " on holdout.");
    Map<String, Object> hashes = new LinkedHashMap<>();
    for (String file : List.of("ratings.dat", "movies.dat"))
      hashes.put(
          file,
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(Files.readAllBytes(root.resolve(file)))));
    report.put("sha256", hashes);
    Map<String, Object> metrics = new LinkedHashMap<>();
    for (var entry : results.entrySet()) {
      var values = entry.getValue();
      var timings = values.stream().mapToDouble(Metrics::millis).sorted().toArray();
      metrics.put(
          entry.getKey(),
          Map.of(
              "recallAt12",
              values.stream().mapToDouble(Metrics::recall).average().orElse(0),
              "precisionAt12",
              values.stream().mapToDouble(Metrics::precision).average().orElse(0),
              "ndcgAt12",
              values.stream().mapToDouble(Metrics::ndcg).average().orElse(0),
              "hitRateAt12",
              values.stream().mapToDouble(Metrics::hitRate).average().orElse(0),
              "genreDiversity",
              values.stream().mapToDouble(Metrics::diversity).average().orElse(0),
              "tailExposure",
              values.stream().mapToDouble(Metrics::tailExposure).average().orElse(0),
              "catalogCoverage",
              coverage.get(entry.getKey()).size() / (double) catalog.size(),
              "meanRankingMs",
              values.stream().mapToDouble(Metrics::millis).average().orElse(0),
              "p95RankingMs",
              timings[(int) Math.ceil(timings.length * .95) - 1]));
    }
    report.put("metrics", metrics);
    Map<String, Object> intervals = new LinkedHashMap<>();
    for (String treatment : discovery ? List.of("balanced", "explore") : List.of("hybrid")) {
      double[] deltas = new double[2000];
      Random random = new Random(42);
      String reference = discovery ? "previousHybrid" : "baseline";
      for (int b = 0; b < deltas.length; b++) {
        double sum = 0;
        for (int i = 0; i < users.size(); i++) {
          int j = random.nextInt(users.size());
          sum += results.get(treatment).get(j).ndcg - results.get(reference).get(j).ndcg;
        }
        deltas[b] = sum / users.size();
      }
      Arrays.sort(deltas);
      intervals.put(treatment + "Minus" + reference, List.of(deltas[50], deltas[1949]));
    }
    report.put(
        "pairedBootstrapNdcgDelta95", discovery ? intervals : intervals.values().iterator().next());
    Path output =
        Path.of(
            discovery
                ? "target/recommendation-discovery-evaluation.json"
                : "target/recommendation-evaluation.json");
    Files.createDirectories(output.getParent());
    String result =
        JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report);
    Files.writeString(output, result);
    System.out.println(result);
  }
}

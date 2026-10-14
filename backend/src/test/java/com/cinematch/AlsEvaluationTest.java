package com.cinematch;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cinematch.library.LibraryMapper;
import com.cinematch.movie.*;
import com.cinematch.recommender.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Two separate invocations: validation selects a weight; test reads that frozen decision. */
class AlsEvaluationTest {
  record Metric(double recall, double precision, double ndcg, double diversity, double millis) {}

  @Test
  void evaluateTrainingAndServingTogether() throws Exception {
    String input = System.getProperty("als.evaluation.data", "");
    assumeTrue(!input.isBlank(), "Optional MovieLens benchmark");
    Path root = Path.of(input);
    String phase = System.getProperty("als.evaluation.phase", "validation");
    assertTrue(Set.of("validation", "test").contains(phase));
    var json = JsonMapper.builder().build();
    List<AlsModel.Rating> all = new ArrayList<>();
    for (String line : Files.readAllLines(root.resolve("ratings.dat"))) {
      var p = line.split(line.contains("::") ? "::" : ",");
      all.add(
          new AlsModel.Rating(
              Long.parseLong(p[0]),
              Long.parseLong(p[1]),
              Double.parseDouble(p[2]),
              Long.parseLong(p[3])));
    }
    all.sort(
        Comparator.comparingLong(AlsModel.Rating::time)
            .thenComparingLong(AlsModel.Rating::user)
            .thenComparingLong(AlsModel.Rating::item));
    long validationCut = all.get((int) (all.size() * .7) - 1).time(),
        testCut = all.get((int) (all.size() * .8) - 1).time();
    long cutoff = phase.equals("validation") ? validationCut : testCut;
    List<AlsModel.Rating> train = all.stream().filter(r -> r.time() <= cutoff).toList();
    List<AlsModel.Rating> future =
        all.stream()
            .filter(r -> r.time() > cutoff && (phase.equals("test") || r.time() <= testCut))
            .toList();
    Map<Long, List<AlsModel.Rating>> history = new TreeMap<>(), holdout = new TreeMap<>();
    Map<Long, double[]> totals = new TreeMap<>();
    for (var r : train) {
      history.computeIfAbsent(r.user(), id -> new ArrayList<>()).add(r);
      var a = totals.computeIfAbsent(r.item(), id -> new double[2]);
      a[0] += r.value();
      a[1]++;
    }
    future.forEach(r -> holdout.computeIfAbsent(r.user(), id -> new ArrayList<>()).add(r));
    List<Movie> catalog = new ArrayList<>();
    for (String line :
        Files.readAllLines(root.resolve("movies.dat"), StandardCharsets.ISO_8859_1)) {
      var p = line.split(line.contains("::") ? "::" : ",", line.contains("::") ? 3 : 2);
      long id = Long.parseLong(p[0]);
      if (!totals.containsKey(id)) continue;
      String title = p.length == 3 ? p[1] : p[1].substring(0, p[1].lastIndexOf(','));
      String genres = p.length == 3 ? p[2] : p[1].substring(p[1].lastIndexOf(',') + 1);
      var year = java.util.regex.Pattern.compile("\\((\\d{4})\\) *$").matcher(title);
      var a = totals.get(id);
      catalog.add(
          new Movie(
              id,
              title,
              title,
              year.find() ? Integer.parseInt(year.group(1)) : 0,
              genres.replace('|', ','),
              0,
              a[0] * 2 / a[1],
              (int) a[1],
              "",
              "",
              "",
              "",
              "",
              false,
              "manual",
              null,
              null,
              null));
    }
    Map<Long, Movie> byId = new TreeMap<>();
    catalog.forEach(m -> byId.put(m.id(), m));
    List<Long> users =
        history.keySet().stream()
            .filter(
                u ->
                    history.get(u).size() >= 20
                        && holdout.getOrDefault(u, List.of()).stream()
                                .filter(r -> r.value() >= 4 && byId.containsKey(r.item()))
                                .count()
                            >= 5)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    Collections.shuffle(users, new Random(20261015));
    users = users.subList(0, Math.min(100, users.size()));
    assertTrue(users.size() >= 30);
    double selectedWeight =
        phase.equals("test")
            ? json.readTree(Path.of("target/als-validation.json").toFile())
                .path("selectedWeight")
                .asDouble()
            : 0;
    List<Double> weights =
        phase.equals("validation") ? List.of(.45, 1.5, 3.0) : List.of(selectedWeight);
    var params = AlsModel.Parameters.defaults();
    long trainingStart = System.nanoTime();
    var als =
        new AlsModel(
            AlsModel.train(
                train, params, n -> System.out.println("ALS " + phase + " iteration " + n)));
    long trainingMillis = (System.nanoTime() - trainingStart) / 1_000_000;
    var rankOne =
        new AlsModel(AlsModel.train(train, new AlsModel.Parameters(1, 15, .1, 42), n -> {}));
    List<String> methods =
        new ArrayList<>(List.of("popularity", "currentHybrid", "alsRank1", "alsRank16"));
    weights.forEach(w -> methods.add("hybridWeight" + w));
    Map<String, List<Metric>> result = new LinkedHashMap<>();
    Map<String, Set<Long>> coverage = new LinkedHashMap<>();
    for (String name : methods) {
      result.put(name, new ArrayList<>());
      coverage.put(name, new HashSet<>());
    }
    double squared = 0;
    int predicted = 0, unknown = 0;
    var service = new RecommendationService();
    for (long user : users) {
      var own = history.get(user);
      Map<Long, Double> ownMap = new HashMap<>();
      own.forEach(r -> ownMap.put(r.item(), r.value()));
      var scores = als.predictions(ownMap);
      var oneScores = rankOne.predictions(ownMap);
      Set<Long> relevant = new HashSet<>();
      for (var r : holdout.get(user)) {
        if (ownMap.containsKey(r.item())) continue;
        if (scores.containsKey(r.item())) {
          squared += Math.pow(scores.get(r.item()) - r.value(), 2);
          predicted++;
        } else unknown++;
        if (r.value() >= 4 && byId.containsKey(r.item())) relevant.add(r.item());
      }
      var saved = own.stream().map(r -> byId.get(r.item())).filter(Objects::nonNull).toList();
      var reviews =
          own.stream().map(r -> new LibraryMapper.Review(r.item(), (int) r.value(), "")).toList();
      var entries = own.stream().map(r -> new LibraryMapper.Entry(r.item(), "WATCHED")).toList();
      List<long[]> overlaps = new ArrayList<>();
      for (var peer : history.entrySet())
        if (peer.getKey() != user) {
          long overlap = peer.getValue().stream().filter(r -> ownMap.containsKey(r.item())).count();
          if (overlap >= 2) overlaps.add(new long[] {peer.getKey(), overlap});
        }
      overlaps.sort(
          Comparator.<long[]>comparingLong(a -> a[1]).reversed().thenComparingLong(a -> a[0]));
      List<LibraryMapper.Signal> signals = new ArrayList<>();
      for (long id : overlaps.stream().limit(200).map(a -> a[0]).sorted().toList())
        history.get(id).stream()
            .sorted(Comparator.comparingLong(AlsModel.Rating::item))
            .forEach(
                r -> {
                  if (signals.size() < 50000)
                    signals.add(new LibraryMapper.Signal(id, r.item(), (int) r.value()));
                });
      for (String name : methods) {
        long start = System.nanoTime();
        List<Movie> picked;
        if (name.equals("popularity"))
          picked =
              catalog.stream()
                  .filter(m -> !ownMap.containsKey(m.id()))
                  .sorted(
                      Comparator.comparingInt(Movie::ratingCount)
                          .reversed()
                          .thenComparingLong(Movie::id))
                  .limit(12)
                  .toList();
        else if (name.startsWith("alsRank")) {
          var values = name.equals("alsRank1") ? oneScores : scores;
          picked =
              catalog.stream()
                  .filter(m -> !ownMap.containsKey(m.id()))
                  .sorted(
                      Comparator.<Movie>comparingDouble(m -> values.get(m.id()))
                          .reversed()
                          .thenComparingLong(Movie::id))
                  .limit(12)
                  .toList();
        } else {
          double weight =
              name.equals("currentHybrid")
                  ? 0
                  : Double.parseDouble(name.substring("hybridWeight".length()));
          picked =
              service
                  .rank(
                      catalog,
                      saved,
                      entries,
                      reviews,
                      List.of(),
                      Set.of(),
                      signals,
                      RecommendationService.Options.DEFAULT,
                      scores,
                      weight)
                  .stream()
                  .map(RecommendationService.Pick::movie)
                  .toList();
        }
        double millis = (System.nanoTime() - start) / 1e6;
        int hits = 0;
        double dcg = 0, idcg = 0, diversity = 0;
        for (int i = 0; i < picked.size(); i++) {
          if (relevant.contains(picked.get(i).id())) {
            hits++;
            dcg += 1 / (Math.log(i + 2) / Math.log(2));
          }
          if (i < relevant.size()) idcg += 1 / (Math.log(i + 2) / Math.log(2));
          for (int j = 0; j < i; j++) {
            Set<String> a = new HashSet<>(List.of(picked.get(i).genres().split(","))),
                b = new HashSet<>(List.of(picked.get(j).genres().split(",")));
            Set<String> union = new HashSet<>(a);
            union.addAll(b);
            a.retainAll(b);
            diversity += 1 - a.size() / (double) union.size();
          }
        }
        result
            .get(name)
            .add(
                new Metric(
                    hits / (double) relevant.size(),
                    hits / 12.0,
                    dcg / idcg,
                    diversity / 66,
                    millis));
        picked.forEach(m -> coverage.get(name).add(m.id()));
      }
    }
    Map<String, Object> metrics = new LinkedHashMap<>();
    for (String name : methods) {
      var rows = result.get(name);
      metrics.put(
          name,
          Map.of(
              "recallAt12",
              rows.stream().mapToDouble(Metric::recall).average().orElseThrow(),
              "precisionAt12",
              rows.stream().mapToDouble(Metric::precision).average().orElseThrow(),
              "ndcgAt12",
              rows.stream().mapToDouble(Metric::ndcg).average().orElseThrow(),
              "genreDiversity",
              rows.stream().mapToDouble(Metric::diversity).average().orElseThrow(),
              "catalogCoverage",
              coverage.get(name).size() / (double) catalog.size(),
              "meanRankingMillis",
              rows.stream().mapToDouble(Metric::millis).average().orElseThrow()));
    }
    if (phase.equals("validation"))
      selectedWeight =
          weights.stream()
              .max(
                  Comparator.comparingDouble(
                      w ->
                          result.get("hybridWeight" + w).stream()
                              .mapToDouble(Metric::ndcg)
                              .average()
                              .orElse(0)))
              .orElseThrow();
    double[] deltas = new double[2000];
    Random random = new Random(42);
    var treatment = result.get("hybridWeight" + selectedWeight);
    var control = result.get("currentHybrid");
    for (int b = 0; b < deltas.length; b++) {
      double sum = 0;
      for (int i = 0; i < users.size(); i++) {
        int j = random.nextInt(users.size());
        sum += treatment.get(j).ndcg() - control.get(j).ndcg();
      }
      deltas[b] = sum / users.size();
    }
    Arrays.sort(deltas);
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("phase", phase);
    report.put(
        "protocol",
        "Global time split 70% training / 10% validation / 20% test, tied timestamps remain in"
            + " earlier partition. Validation selects fusion weight only. Test retrains on first"
            + " 80% with frozen parameters and weight. >=20 past ratings and >=5 eligible future"
            + " positives; deterministic sample <=100 users. Full training catalog minus seen;"
            + " positive >=4; k=12. Users with no history and new items are not evaluated by"
            + " ranking; unknown test items counted for RMSE coverage. Same benchmark family as"
            + " earlier reports, not a fresh independent dataset.");
    report.put("validationCutoff", validationCut);
    report.put("testCutoff", testCut);
    report.put("trainRatings", train.size());
    report.put("futureRatings", future.size());
    report.put("sampleUserIds", users);
    report.put("catalogMovies", catalog.size());
    report.put("parameters", params);
    report.put("selectedWeight", selectedWeight);
    report.put("trainingMillis", trainingMillis);
    report.put("metrics", metrics);
    report.put("alsRank16Rmse", Math.sqrt(squared / predicted));
    report.put("rmsePredictedRatings", predicted);
    report.put("rmseUnknownItems", unknown);
    report.put("pairedBootstrapNdcgDelta95", List.of(deltas[50], deltas[1949]));
    report.put(
        "ratingsSha256",
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(root.resolve("ratings.dat")))));
    Files.writeString(
        Path.of("target/als-" + phase + ".json"),
        json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
  }
}

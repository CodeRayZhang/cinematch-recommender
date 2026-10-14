package com.cinematch.recommender;

import java.util.*;
import java.util.function.IntConsumer;

/** Explicit-feedback ALS-WR, including the same least-squares fold-in used online. */
public final class AlsModel {
  public record Rating(long user, long item, double value, long time) {}

  public record Parameters(int rank, int iterations, double regularization, long seed) {
    public Parameters {
      if (rank < 1
          || rank > 32
          || iterations < 1
          || iterations > 30
          || !Double.isFinite(regularization)
          || regularization < .01
          || regularization > 10) throw new IllegalArgumentException("训练参数超出允许范围");
    }

    public static Parameters defaults() {
      return new Parameters(16, 15, .1, 42);
    }
  }

  public record Artifact(String format, Parameters parameters, long[] itemIds, double[][] factors) {
    public Artifact {
      if (!"cinematch.als.v1".equals(format)
          || parameters == null
          || itemIds == null
          || factors == null
          || itemIds.length == 0
          || itemIds.length > 10000
          || itemIds.length != factors.length) throw new IllegalArgumentException("模型文件结构无效");
      Set<Long> ids = new HashSet<>();
      for (int i = 0; i < factors.length; i++) {
        if (!ids.add(itemIds[i]) || factors[i] == null || factors[i].length != parameters.rank())
          throw new IllegalArgumentException("模型电影或维度重复 / 不匹配");
        for (double v : factors[i])
          if (!Double.isFinite(v) || Math.abs(v) > 1e6)
            throw new IllegalArgumentException("模型包含无效权重");
      }
    }
  }

  private record Edge(int other, double value) {}

  public static Artifact train(List<Rating> ratings, Parameters p, IntConsumer progress) {
    if (ratings.isEmpty() || ratings.size() > 2_000_000)
      throw new IllegalArgumentException("评分数量须为 1–200 万");
    long[] users = ratings.stream().mapToLong(Rating::user).distinct().sorted().toArray();
    long[] items = ratings.stream().mapToLong(Rating::item).distinct().sorted().toArray();
    if (users.length > 100000 || items.length > 10000)
      throw new IllegalArgumentException("训练规模超过单机上限");
    var u = indices(users);
    var m = indices(items);
    List<List<Edge>> byUser = rows(users.length), byItem = rows(items.length);
    Set<Long> pairs = new HashSet<>();
    for (Rating r : ratings) {
      if (!Double.isFinite(r.value())
          || r.value() < .5
          || r.value() > 5
          || !pairs.add(((long) u.get(r.user()) << 32) | m.get(r.item())))
        throw new IllegalArgumentException("评分数值无效或存在重复用户电影对");
      int a = u.get(r.user()), b = m.get(r.item());
      byUser.get(a).add(new Edge(b, r.value()));
      byItem.get(b).add(new Edge(a, r.value()));
    }
    double[][] x = new double[users.length][p.rank()], y = new double[items.length][p.rank()];
    Random random = new Random(p.seed());
    for (double[] vector : y)
      for (int k = 0; k < vector.length; k++) vector[k] = random.nextGaussian() * .1;
    for (int iteration = 0; iteration < p.iterations(); iteration++) {
      update(x, byUser, y, p);
      update(y, byItem, x, p);
      progress.accept(iteration + 1);
    }
    return new Artifact("cinematch.als.v1", p, items, y);
  }

  private static Map<Long, Integer> indices(long[] ids) {
    Map<Long, Integer> result = new HashMap<>();
    for (int i = 0; i < ids.length; i++) result.put(ids[i], i);
    return result;
  }

  private static List<List<Edge>> rows(int n) {
    List<List<Edge>> rows = new ArrayList<>(n);
    for (int i = 0; i < n; i++) rows.add(new ArrayList<>());
    return rows;
  }

  private static void update(
      double[][] target, List<List<Edge>> rows, double[][] other, Parameters p) {
    for (int i = 0; i < target.length; i++) {
      if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("训练已中断");
      target[i] = fit(rows.get(i), other, p);
    }
  }

  private static double[] fit(List<Edge> edges, double[][] other, Parameters p) {
    int n = p.rank();
    double[][] a = new double[n][n];
    double[] b = new double[n];
    for (Edge e : edges) {
      double[] v = other[e.other()];
      for (int j = 0; j < n; j++) {
        b[j] += e.value() * v[j];
        for (int k = 0; k <= j; k++) a[j][k] += v[j] * v[k];
      }
    }
    for (int j = 0; j < n; j++) {
      a[j][j] += p.regularization() * Math.max(1, edges.size());
      for (int k = 0; k < j; k++) a[k][j] = a[j][k];
    }
    // Cholesky solves the symmetric positive definite ridge system without matrix inversion.
    for (int j = 0; j < n; j++)
      for (int k = 0; k <= j; k++) {
        double sum = a[j][k];
        for (int l = 0; l < k; l++) sum -= a[j][l] * a[k][l];
        if (j == k && !(sum > 0)) throw new IllegalStateException("模型数值不稳定");
        a[j][k] = j == k ? Math.sqrt(sum) : sum / a[k][k];
      }
    for (int j = 0; j < n; j++) {
      for (int k = 0; k < j; k++) b[j] -= a[j][k] * b[k];
      b[j] /= a[j][j];
    }
    for (int j = n - 1; j >= 0; j--) {
      for (int k = j + 1; k < n; k++) b[j] -= a[k][j] * b[k];
      b[j] /= a[j][j];
    }
    return b;
  }

  private final Artifact artifact;
  private final Map<Long, Integer> positions;

  public AlsModel(Artifact artifact) {
    this.artifact = artifact;
    this.positions = indices(artifact.itemIds());
  }

  public Artifact artifact() {
    return artifact;
  }

  public boolean contains(long item) {
    return positions.containsKey(item);
  }

  public int supported(Map<Long, Double> ratings) {
    return (int) ratings.keySet().stream().filter(positions::containsKey).count();
  }

  public double[] foldIn(Map<Long, Double> ratings) {
    List<Edge> edges = new ArrayList<>();
    ratings.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            e -> {
              if (!Double.isFinite(e.getValue()) || e.getValue() < .5 || e.getValue() > 5)
                throw new IllegalArgumentException("评分无效");
              Integer index = positions.get(e.getKey());
              if (index != null) edges.add(new Edge(index, e.getValue()));
            });
    return fit(edges, artifact.factors(), artifact.parameters());
  }

  public OptionalDouble predict(double[] user, long item) {
    if (user == null || user.length != artifact.parameters().rank())
      throw new IllegalArgumentException("个人向量维度不匹配");
    for (double value : user)
      if (!Double.isFinite(value)) throw new IllegalArgumentException("个人向量无效");
    Integer index = positions.get(item);
    if (index == null) return OptionalDouble.empty();
    double value = 0;
    double[] vector = artifact.factors()[index];
    for (int k = 0; k < user.length; k++) value += user[k] * vector[k];
    return OptionalDouble.of(Math.max(.5, Math.min(5, value)));
  }

  public Map<Long, Double> predictions(Map<Long, Double> ratings) {
    double[] user = foldIn(ratings);
    Map<Long, Double> result = new HashMap<>();
    for (long id : artifact.itemIds()) result.put(id, predict(user, id).orElseThrow());
    return result;
  }
}

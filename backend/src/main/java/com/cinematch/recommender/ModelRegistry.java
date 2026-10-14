package com.cinematch.recommender;

import com.cinematch.movie.*;
import jakarta.annotation.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ModelRegistry implements ApplicationRunner {
  public record Version(
      String id,
      String dataset,
      String status,
      int iteration,
      AlsModel.Parameters parameters,
      Map<String, Object> summary,
      String error,
      String createdAt,
      String completedAt,
      boolean active) {}

  public record Loaded(String id, AlsModel model, List<TrainingData.Mapping> mappings) {}

  private final JdbcTemplate jdbc;
  private final TrainingData data;
  private final TransactionTemplate transactions;
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(Thread.ofPlatform().name("als-training").factory());
  private final AtomicBoolean training = new AtomicBoolean();
  private final JsonMapper json = JsonMapper.builder().build();
  private volatile Loaded cached;
  private final String startupDataset;
  private final boolean startupActivate;

  public ModelRegistry(
      JdbcTemplate jdbc,
      TrainingData data,
      TransactionTemplate transactions,
      @Value("${app.recommender.train:}") String startupDataset,
      @Value("${app.recommender.activate:false}") boolean startupActivate) {
    this.jdbc = jdbc;
    this.data = data;
    this.transactions = transactions;
    this.startupDataset = startupDataset;
    this.startupActivate = startupActivate;
  }

  @PostConstruct
  void recover() {
    jdbc.update(
        "UPDATE recommendation_models SET"
            + " status='FAILED',error_message='应用重启中断了训练，请重新创建任务',completed_at=CURRENT_TIMESTAMP"
            + " WHERE status IN ('QUEUED','RUNNING')");
  }

  @PreDestroy
  void stop() {
    worker.shutdownNow();
  }

  @Override
  public void run(ApplicationArguments ignored) {
    if (!startupDataset.isBlank())
      start(startupDataset, AlsModel.Parameters.defaults(), startupActivate);
  }

  public List<TrainingData.Dataset> datasets() {
    return data.datasets();
  }

  public List<Version> list() {
    String active = activeId();
    return jdbc.query(
        "SELECT"
            + " id,dataset,status,iteration,parameters_json,summary_json,error_message,created_at,completed_at"
            + " FROM recommendation_models ORDER BY created_at DESC,id DESC",
        (r, n) -> {
          var summary = r.getString("summary_json");
          @SuppressWarnings("unchecked")
          Map<String, Object> details =
              summary == null ? Map.of() : json.readValue(summary, Map.class);
          return new Version(
              r.getString("id"),
              r.getString("dataset"),
              r.getString("status"),
              r.getInt("iteration"),
              json.readValue(r.getString("parameters_json"), AlsModel.Parameters.class),
              details,
              r.getString("error_message"),
              r.getTimestamp("created_at").toInstant().toString(),
              r.getTimestamp("completed_at") == null
                  ? null
                  : r.getTimestamp("completed_at").toInstant().toString(),
              Objects.equals(active, r.getString("id")));
        });
  }

  public String start(String dataset, AlsModel.Parameters parameters, boolean activate) {
    if (!data.datasets().stream().anyMatch(d -> d.name().equals(dataset) && d.available()))
      throw bad("数据集尚未准备好");
    if (!training.compareAndSet(false, true)) throw conflict("已有训练任务正在执行");
    String id = UUID.randomUUID().toString();
    try {
      transactions.executeWithoutResult(
          status -> {
            jdbc.queryForObject(
                "SELECT id FROM recommendation_model_state WHERE id=1 FOR UPDATE", Integer.class);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_models", Long.class) >= 10)
              throw conflict("最多保留 10 个模型任务，请删除不再需要的非活动版本");
            jdbc.update(
                "INSERT INTO recommendation_models(id,dataset,status,parameters_json)"
                    + " VALUES(?,?,'QUEUED',?)",
                id,
                dataset,
                json.writeValueAsString(parameters));
          });
      worker.submit(() -> train(id, dataset, parameters, activate));
    } catch (RuntimeException e) {
      training.set(false);
      throw e;
    }
    return id;
  }

  private void train(String id, String dataset, AlsModel.Parameters parameters, boolean activate) {
    try {
      jdbc.update("UPDATE recommendation_models SET status='RUNNING' WHERE id=?", id);
      var input = data.load(dataset, id);
      long start = System.nanoTime();
      var artifact =
          AlsModel.train(
              input.ratings(),
              parameters,
              iteration ->
                  jdbc.update(
                      "UPDATE recommendation_models SET iteration=? WHERE id=?", iteration, id));
      Map<String, Object> summary = new LinkedHashMap<>(input.summary());
      summary.put("trainingMillis", (System.nanoTime() - start) / 1_000_000);
      summary.put("algorithm", "explicit-als-wr");
      summary.put("evaluated", false);
      summary.put("trainedAt", Instant.now().toString());
      jdbc.update(
          "UPDATE recommendation_models SET"
              + " status='READY',summary_json=?,artifact_json=?,mappings_json=?,completed_at=CURRENT_TIMESTAMP"
              + " WHERE id=?",
          json.writeValueAsString(summary),
          json.writeValueAsString(artifact),
          json.writeValueAsString(input.mappings()),
          id);
      if (activate) activate(id);
    } catch (Exception e) {
      String message =
          e instanceof IllegalArgumentException ? e.getMessage() : "训练未完成，请检查服务器日志与数据文件";
      jdbc.update(
          "UPDATE recommendation_models SET"
              + " status='FAILED',error_message=?,completed_at=CURRENT_TIMESTAMP WHERE id=?",
          message.substring(0, Math.min(500, message.length())),
          id);
      org.slf4j.LoggerFactory.getLogger(ModelRegistry.class)
          .warn("Recommendation training {} failed", id, e);
    } finally {
      training.set(false);
    }
  }

  public String activeId() {
    return jdbc.queryForObject(
        "SELECT active_model_id FROM recommendation_model_state WHERE id=1", String.class);
  }

  private Loaded load(String id) {
    var rows =
        jdbc.query(
            "SELECT artifact_json,mappings_json FROM recommendation_models WHERE id=? AND"
                + " status='READY'",
            (r, n) ->
                new Loaded(
                    id,
                    new AlsModel(json.readValue(r.getString(1), AlsModel.Artifact.class)),
                    List.of(json.readValue(r.getString(2), TrainingData.Mapping[].class))),
            id);
    if (rows.isEmpty()) throw bad("该版本尚未训练完成或不存在");
    return rows.getFirst();
  }

  public synchronized Loaded active() {
    String id = activeId();
    if (id == null) {
      cached = null;
      return null;
    }
    if (cached != null && cached.id().equals(id)) return cached;
    try {
      cached = load(id);
      return cached;
    } catch (RuntimeException e) {
      org.slf4j.LoggerFactory.getLogger(ModelRegistry.class)
          .error("Active recommendation model {} cannot load; using hybrid fallback", id, e);
      return null;
    }
  }

  public void activate(String id) {
    transactions.executeWithoutResult(
        status -> {
          jdbc.queryForObject(
              "SELECT id FROM recommendation_model_state WHERE id=1 FOR UPDATE", Integer.class);
          var loaded = load(id);
          Map<Long, Movie> catalog = new HashMap<>();
          jdbc.query("SELECT * FROM movies", (r, n) -> MovieRows.read(r))
              .forEach(m -> catalog.put(m.id(), m));
          long valid =
              loaded.mappings().stream()
                  .filter(
                      m ->
                          catalog.containsKey(m.movieId())
                              && m.matches(catalog.get(m.movieId()))
                              && loaded.model().contains(m.itemId()))
                  .count();
          if (valid < 3) throw bad("有效电影映射不足，不能启用该模型");
          jdbc.update("UPDATE recommendation_model_state SET active_model_id=? WHERE id=1", id);
        });
    cached = null;
  }

  public void disable() {
    transactions.executeWithoutResult(
        s -> {
          jdbc.queryForObject(
              "SELECT id FROM recommendation_model_state WHERE id=1 FOR UPDATE", Integer.class);
          jdbc.update("UPDATE recommendation_model_state SET active_model_id=NULL WHERE id=1");
        });
    cached = null;
  }

  public void delete(String id) {
    transactions.executeWithoutResult(
        s -> {
          jdbc.queryForObject(
              "SELECT id FROM recommendation_model_state WHERE id=1 FOR UPDATE", Integer.class);
          if (id.equals(activeId())) throw conflict("请先切换或停用当前模型");
          var states =
              jdbc.queryForList(
                  "SELECT status FROM recommendation_models WHERE id=?", String.class, id);
          if (states.stream().anyMatch(x -> Set.of("QUEUED", "RUNNING").contains(x)))
            throw conflict("训练中的任务不能删除");
          jdbc.update("DELETE FROM recommendation_models WHERE id=?", id);
        });
  }

  public Map<String, Object> export(String id) {
    var model = load(id);
    return Map.of(
        "format",
        "cinematch.model-export.v1",
        "id",
        id,
        "artifact",
        model.model().artifact(),
        "mappings",
        model.mappings(),
        "version",
        list().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow());
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }
}

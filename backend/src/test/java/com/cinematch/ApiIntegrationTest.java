package com.cinematch;

import static org.junit.jupiter.api.Assertions.*;

import com.cinematch.auth.AccountMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.recommender.data-directory=${java.io.tmpdir}/cinematch-model-tests",
      "app.admin.username=",
      "app.admin.password="
    })
class ApiIntegrationTest {
  @LocalServerPort int port;
  @Autowired AccountMapper accounts;
  @Autowired PasswordEncoder passwords;
  @Autowired com.cinematch.movie.RecommendationObservation observation;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired com.cinematch.movie.CatalogCache catalogCache;
  HttpClient client;
  final JsonMapper json = JsonMapper.builder().build();

  @BeforeEach
  void setup() {
    client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
  }

  HttpResponse<String> send(String method, String path, Object body, boolean csrf)
      throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path));
    if (csrf)
      builder.header(
          "X-XSRF-TOKEN", read(send("GET", "/session", null, false)).path("csrfToken").asText());
    if (body instanceof String text) {
      builder
          .header("Content-Type", "application/x-www-form-urlencoded")
          .method(method, HttpRequest.BodyPublishers.ofString(text));
    } else if (body != null) {
      builder
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    } else builder.method(method, HttpRequest.BodyPublishers.noBody());
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  JsonNode read(HttpResponse<String> response) {
    return json.readTree(response.body());
  }

  void status(int expected, HttpResponse<String> response) {
    assertEquals(expected, response.statusCode(), response.body());
  }

  String newAccount(String role) {
    String name = "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    accounts.insert(name, "测试影迷", passwords.encode("Test-password-832!"), role);
    return name;
  }

  void login(String username) throws Exception {
    status(
        200,
        send("POST", "/auth/login", "username=" + username + "&password=Test-password-832!", true));
  }

  @Test
  void catalogSupportsRealFilteringPagingAndNotFound() throws Exception {
    var all = send("GET", "/movies?size=3", null, false);
    status(200, all);
    assertEquals(3, read(all).path("items").size());
    assertTrue(read(all).path("total").asInt() >= 18);
    var search = send("GET", "/movies?q=Interstellar", null, false);
    assertEquals(1, read(search).path("items").size());
    var filter =
        send(
            "GET",
            "/movies?genre=" + URLEncoder.encode("动画", java.nio.charset.StandardCharsets.UTF_8),
            null,
            false);
    for (var movie : read(filter).path("items"))
      assertTrue(movie.path("genres").asText().contains("动画"));
    status(404, send("GET", "/movies/99999999", null, false));
    status(400, send("GET", "/movies?page=0", null, false));
    status(400, send("GET", "/movies?sort=rating%3BDELETE", null, false));
  }

  @Test
  void csrfAndRoleChecksProtectWrites() throws Exception {
    status(401, send("GET", "/me/library", null, false));
    status(403, send("PUT", "/me/library/1", Map.of("status", "WATCHLIST"), false));
    login(newAccount("USER"));
    status(403, send("GET", "/admin/stats", null, false));
    status(403, send("DELETE", "/admin/movies/1", null, true));
    status(403, send("PUT", "/me/library/1", Map.of("status", "WATCHLIST"), false));
  }

  @Test
  void registrationHashesPasswordAndNeverReturnsIt() throws Exception {
    String name = "signup_" + System.nanoTime();
    var input = Map.of("username", name, "displayName", "新影迷", "password", "Test-password-832!");
    var response = send("POST", "/auth/register", input, true);
    status(201, response);
    assertFalse(response.body().contains("password"));
    assertTrue(
        passwords.matches("Test-password-832!", accounts.findByUsername(name).passwordHash()));
    status(409, send("POST", "/auth/register", input, true));
    status(
        400,
        send(
            "POST",
            "/auth/register",
            Map.of("username", "short", "displayName", "影迷", "password", "123"),
            true));
    login(name);
    assertEquals(
        name, read(send("GET", "/session", null, false)).path("user").path("username").asText());
    status(200, send("POST", "/auth/logout", null, true));
    status(401, send("GET", "/me/library", null, false));
  }

  @Test
  void libraryIsIsolatedAndUpsertsAreIdempotent() throws Exception {
    login(newAccount("USER"));
    status(200, send("PUT", "/me/library/1", Map.of("status", "WATCHLIST"), true));
    status(200, send("PUT", "/me/library/1", Map.of("status", "WATCHLIST"), true));
    var own = read(send("GET", "/me/library", null, false));
    assertEquals(1, own.path("entries").size());
    status(400, send("PUT", "/me/library/1", Map.of("status", "INVALID"), true));
    status(400, send("PUT", "/me/library/1", Map.of("status", "WATCHLIST", "userId", 99), true));
    status(404, send("PUT", "/me/library/99999999", Map.of("status", "WATCHLIST"), true));
    status(200, send("POST", "/auth/logout", null, true));
    login(newAccount("USER"));
    assertEquals(0, read(send("GET", "/me/library", null, false)).path("entries").size());
    status(200, send("DELETE", "/me/library/1", null, true));
  }

  @Test
  void ratingMarksWatchedAndRecommendationsExcludeInteractions() throws Exception {
    login(newAccount("USER"));
    status(400, send("PUT", "/me/reviews/1", Map.of("stars", 6, "body", ""), true));
    status(200, send("PUT", "/me/reviews/1", Map.of("stars", 5, "body", "爱与时间"), true));
    var own = read(send("GET", "/me/library", null, false));
    assertEquals("WATCHED", own.path("entries").get(0).path("status").asText());
    assertEquals(5, own.path("reviews").get(0).path("stars").asInt());
    status(200, send("PUT", "/me/reviews/1", Map.of("stars", 4, "body", "更新短评"), true));
    assertEquals(1, read(send("GET", "/me/library", null, false)).path("reviews").size());
    for (var pick : read(send("GET", "/recommendations", null, false)))
      assertNotEquals(1, pick.path("movie").path("id").asLong());
  }

  @Test
  void removingFromShelfDoesNotEraseRatingPreferences() throws Exception {
    login(newAccount("USER"));
    status(200, send("PUT", "/me/reviews/1", Map.of("stars", 5, "body", ""), true));
    status(200, send("DELETE", "/me/library/1", null, true));
    var result = read(send("GET", "/recommendations", null, false));
    assertTrue(result.toString().contains("因为你偏爱"));
    for (var pick : result) assertNotEquals(1, pick.path("movie").path("id").asLong());
  }

  @Test
  void adminCanCreateEditAndDeleteMovies() throws Exception {
    login(newAccount("ADMIN"));
    status(200, send("GET", "/admin/stats", null, false));
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("title", "集成测试电影");
    input.put("originalTitle", "Integration Movie");
    input.put("releaseYear", 2026);
    input.put("genres", "剧情");
    input.put("runtime", 90);
    input.put("rating", 8.0);
    input.put("ratingCount", 10);
    input.put("director", "Test Director");
    input.put("country", "中国");
    input.put("synopsis", "一个测试故事。");
    input.put("posterUrl", "/images/placeholder.svg");
    input.put("backdropUrl", "/images/placeholder.svg");
    input.put("featured", false);
    catalogCache.candidates();
    status(201, send("POST", "/admin/movies", input, true));
    long id =
        read(send("GET", "/movies?q=Integration%20Movie", null, false))
            .path("items")
            .get(0)
            .path("id")
            .asLong();
    assertTrue(catalogCache.candidates().stream().anyMatch(m -> m.id() == id));
    input.put("title", "更新的电影");
    status(200, send("PUT", "/admin/movies/" + id, input, true));
    assertEquals("更新的电影", read(send("GET", "/movies/" + id, null, false)).path("title").asText());
    assertTrue(
        catalogCache.candidates().stream()
            .anyMatch(m -> m.id() == id && m.title().equals("更新的电影")));
    status(200, send("DELETE", "/admin/movies/" + id, null, true));
    assertFalse(catalogCache.candidates().stream().anyMatch(m -> m.id() == id));
    status(404, send("GET", "/movies/" + id, null, false));
  }

  @Test
  void personalProfileIsValidatedPrivateAndCannotChangeRole() throws Exception {
    status(401, send("GET", "/me/profile", null, false));
    String first = newAccount("USER");
    login(first);
    var profile =
        Map.of("displayName", "  新昵称  ", "bio", " 科幻与生活 ", "preferredGenres", List.of("科幻", "动画"));
    status(403, send("PUT", "/me/profile", profile, false));
    status(200, send("PUT", "/me/profile", profile, true));
    var own = read(send("GET", "/me/profile", null, false));
    assertEquals("新昵称", own.path("displayName").asText());
    assertEquals("科幻,动画", own.path("preferredGenres").asText());
    assertFalse(own.toString().contains("password"));
    status(
        400,
        send(
            "PUT",
            "/me/profile",
            Map.of("displayName", "x", "bio", "", "preferredGenres", List.of("invalid")),
            true));
    status(
        400,
        send(
            "PUT",
            "/me/profile",
            Map.of("displayName", "x", "bio", "", "preferredGenres", List.of(), "role", "ADMIN"),
            true));
    login(newAccount("USER"));
    assertEquals("", read(send("GET", "/me/profile", null, false)).path("bio").asText());
    login(first);
    assertEquals(
        "新昵称",
        read(send("GET", "/session", null, false)).path("user").path("displayName").asText());
  }

  @Test
  void batchShelfUpdatesAreAtomicBoundedAndPrivate() throws Exception {
    String first = newAccount("USER");
    login(first);
    status(
        404,
        send(
            "PUT",
            "/me/library/batch",
            Map.of("movieIds", List.of(1, 99999999), "action", "WATCHED"),
            true));
    assertEquals(0, read(send("GET", "/me/library", null, false)).path("entries").size());
    status(
        400,
        send(
            "PUT",
            "/me/library/batch",
            Map.of("movieIds", Collections.nCopies(101, 1), "action", "WATCHED"),
            true));
    status(
        400,
        send("PUT", "/me/library/batch", Map.of("movieIds", List.of(), "action", "WATCHED"), true));
    status(
        200,
        send(
            "PUT",
            "/me/library/batch",
            Map.of("movieIds", List.of(1, 2, 1), "action", "WATCHED"),
            true));
    assertEquals(2, read(send("GET", "/me/library", null, false)).path("entries").size());
    status(
        200,
        send(
            "PUT",
            "/me/library/batch",
            Map.of("movieIds", List.of(1, 2), "action", "WATCHLIST"),
            true));
    for (var entry : read(send("GET", "/me/library", null, false)).path("entries"))
      assertEquals("WATCHLIST", entry.path("status").asText());
    login(newAccount("USER"));
    status(
        200,
        send(
            "PUT",
            "/me/library/batch",
            Map.of("movieIds", List.of(1, 2), "action", "REMOVE"),
            true));
    login(first);
    assertEquals(2, read(send("GET", "/me/library", null, false)).path("entries").size());
  }

  @Test
  void reviewArchiveSurvivesShelfRemovalAndDeletionKeepsWatchedStatus() throws Exception {
    login(newAccount("USER"));
    status(200, send("PUT", "/me/reviews/1", Map.of("stars", 5, "body", "保留这段感受"), true));
    status(
        200,
        send("PUT", "/me/library/batch", Map.of("movieIds", List.of(1), "action", "REMOVE"), true));
    var archive = read(send("GET", "/me/library", null, false));
    assertEquals(0, archive.path("entries").size());
    assertEquals(1, archive.path("movies").size());
    assertEquals(1, archive.path("reviews").size());
    status(200, send("PUT", "/me/library/1", Map.of("status", "WATCHED"), true));
    status(200, send("DELETE", "/me/reviews/1", null, true));
    var after = read(send("GET", "/me/library", null, false));
    assertEquals(0, after.path("reviews").size());
    assertEquals("WATCHED", after.path("entries").get(0).path("status").asText());
  }

  @Test
  void recommendationFeedbackIsRestorableAndScopedToCurrentUser() throws Exception {
    String first = newAccount("USER");
    login(first);
    long id =
        read(send("GET", "/recommendations", null, false)).get(0).path("movie").path("id").asLong();
    status(200, send("PUT", "/me/feedback/" + id, null, true));
    status(200, send("PUT", "/me/feedback/" + id, null, true));
    assertEquals(1, read(send("GET", "/me/feedback", null, false)).size());
    for (var pick : read(send("GET", "/recommendations", null, false)))
      assertNotEquals(id, pick.path("movie").path("id").asLong());
    login(newAccount("USER"));
    assertEquals(0, read(send("GET", "/me/feedback", null, false)).size());
    status(200, send("DELETE", "/me/feedback/" + id, null, true));
    login(first);
    assertEquals(1, read(send("GET", "/me/feedback", null, false)).size());
    status(200, send("DELETE", "/me/feedback/" + id, null, true));
    assertEquals(0, read(send("GET", "/me/feedback", null, false)).size());
  }

  @Test
  void discoveryFiltersWorkForGuestsAndBatchesNeverRepeat() throws Exception {
    var response =
        send(
            "GET",
            "/recommendations?mode=explore&maxRuntime=120&genres="
                + URLEncoder.encode("动画", java.nio.charset.StandardCharsets.UTF_8),
            null,
            false);
    status(200, response);
    var filtered = read(response);
    assertFalse(filtered.isEmpty());
    for (var pick : filtered) {
      assertTrue(pick.path("movie").path("genres").asText().contains("动画"));
      assertTrue(pick.path("movie").path("runtime").asInt() > 0);
      assertTrue(pick.path("movie").path("runtime").asInt() <= 120);
      assertTrue(pick.path("exploration").isBoolean());
    }
    var first = read(send("GET", "/recommendations", null, false));
    Set<Long> ids = new HashSet<>();
    for (var pick : first) ids.add(pick.path("movie").path("id").asLong());
    String excluded = String.join(",", ids.stream().map(String::valueOf).toList());
    var second = read(send("GET", "/recommendations?exclude=" + excluded, null, false));
    assertFalse(second.isEmpty());
    for (var pick : second) assertFalse(ids.contains(pick.path("movie").path("id").asLong()));
    // Browsing another batch is temporary: it must not write permanent feedback.
    login(newAccount("USER"));
    status(200, send("GET", "/recommendations?exclude=" + excluded, null, false));
    assertEquals(0, read(send("GET", "/me/feedback", null, false)).size());
  }

  @Test
  void discoveryRejectsInvalidAndOversizedParameters() throws Exception {
    for (String query :
        List.of(
            "mode=random",
            "maxRuntime=-1",
            "maxRuntime=121",
            "maxRuntime=bad",
            "genres=invalid",
            "exclude=0",
            "exclude=-1",
            "exclude=1,",
            "exclude=abc",
            "exclude=9223372036854775808",
            "exclude=" + String.join(",", Collections.nCopies(121, "1")),
            "genres=" + URLEncoder.encode("剧情,动画,科幻,爱情", java.nio.charset.StandardCharsets.UTF_8)))
      status(400, send("GET", "/recommendations?" + query, null, false));
  }

  @Test
  void namedCollectionsArePrivateAtomicOrderedAndExportable() throws Exception {
    String owner = newAccount("USER");
    login(owner);
    status(403, send("POST", "/me/collections", Map.of("name", "周末", "description", ""), false));
    var created =
        send(
            "POST", "/me/collections", Map.of("name", " 周末电影 ", "description", " 给周末留两个小时 "), true);
    status(201, created);
    String id = read(created).path("id").asText();
    String path = "/me/collections/" + id;
    status(200, send("POST", path + "/movies", Map.of("movieIds", List.of(1, 2, 1)), true));
    status(404, send("POST", path + "/movies", Map.of("movieIds", List.of(3, 99999999)), true));
    var detail = read(send("GET", path + "?size=1", null, false));
    assertEquals(2, detail.path("total").asInt());
    assertEquals(1, detail.path("items").size());
    assertEquals("周末电影", detail.path("collection").path("name").asText());
    status(
        400, send("PUT", path, Map.of("name", "周末电影", "description", "", "coverMovieId", 3), true));
    status(
        200, send("PUT", path, Map.of("name", "周末电影", "description", "", "coverMovieId", 2), true));
    status(200, send("PUT", path + "/movies/2", Map.of("note", " 重看这一个场景 "), true));
    status(200, send("POST", path + "/movies/2/move", Map.of("direction", "up"), true));
    var exported = read(send("GET", path + "/export", null, false));
    assertEquals("cinematch.collection.v1", exported.path("format").asText());
    assertEquals(2, exported.path("items").get(0).path("movie").path("id").asLong());
    assertEquals("重看这一个场景", exported.path("items").get(0).path("note").asText());
    login(newAccount("USER"));
    assertEquals(0, read(send("GET", "/me/collections", null, false)).size());
    status(404, send("GET", path + "/export", null, false));
    status(404, send("POST", path + "/movies", Map.of("movieIds", List.of(3)), true));
    status(404, send("DELETE", path, null, true));
    login(owner);
    status(200, send("PUT", "/me/reviews/2", Map.of("stars", 5, "body", "独立保存的评分"), true));
    status(200, send("DELETE", path + "/movies/2", null, true));
    assertTrue(
        read(send("GET", path, null, false)).path("collection").path("coverMovieId").isNull());
    status(200, send("DELETE", path, null, true));
    assertEquals(5, read(send("GET", "/me/reviews/2", null, false)).path("stars").asInt());
    status(404, send("GET", path, null, false));
  }

  @Test
  void libraryPagesAndSummaryPreserveRemovedRatingsAndUndoIsAtomic() throws Exception {
    login(newAccount("USER"));
    status(
        200,
        send(
            "PUT",
            "/me/library/batch",
            Map.of("movieIds", List.of(1, 2, 3, 4), "action", "WATCHLIST"),
            true));
    status(200, send("PUT", "/me/reviews/1", Map.of("stars", 5, "body", "分页仍保留短评"), true));
    status(200, send("PUT", "/me/reviews/2", Map.of("stars", 3, "body", ""), true));
    status(200, send("DELETE", "/me/library/1", null, true));
    var state = read(send("GET", "/me/library/state", null, false));
    assertEquals(0, state.path("movies").size());
    assertEquals(3, state.path("entries").size());
    for (var review : state.path("reviews")) assertEquals("", review.path("body").asText());
    var all = read(send("GET", "/me/library/page?size=1", null, false));
    assertEquals(3, all.path("total").asLong());
    assertEquals(1, all.path("items").size());
    var rated = read(send("GET", "/me/library/page?tab=RATED&sort=rating&size=1", null, false));
    assertEquals(2, rated.path("total").asLong());
    assertEquals(1, rated.path("items").get(0).path("movie").path("id").asLong());
    assertEquals("分页仍保留短评", rated.path("items").get(0).path("body").asText());
    var summary = read(send("GET", "/me/library/summary", null, false));
    assertEquals(2, summary.path("watching").asLong());
    assertEquals(1, summary.path("watched").asLong());
    assertEquals(4.0, summary.path("average").asDouble());
    var entries = new ArrayList<Map<String, Object>>();
    Map<String, Object> remove = new HashMap<>();
    remove.put("movieId", 2);
    remove.put("status", null);
    entries.add(remove);
    entries.add(Map.of("movieId", 3, "status", "WATCHED"));
    entries.add(Map.of("movieId", 99999999, "status", "WATCHED"));
    status(404, send("PUT", "/me/library/restore", Map.of("entries", entries), true));
    assertEquals(3, read(send("GET", "/me/library/state", null, false)).path("entries").size());
    entries.removeLast();
    status(200, send("PUT", "/me/library/restore", Map.of("entries", entries), true));
    assertEquals(2, read(send("GET", "/me/library/state", null, false)).path("entries").size());
    status(400, send("GET", "/me/library/page?sort=invalid", null, false));
    status(400, send("GET", "/me/library/page?size=49", null, false));
    login(newAccount("USER"));
    assertEquals(
        0, read(send("GET", "/me/library/page?tab=RATED", null, false)).path("total").asLong());
  }

  @Test
  void catalogCombinesGenreYearsAndRuntimeWithStablePaging() throws Exception {
    var query = "/movies?yearFrom=1990&yearTo=2020&maxRuntime=120&size=2&sort=recent";
    var first = read(send("GET", query, null, false));
    assertTrue(first.path("total").asLong() > 2);
    Set<Long> ids = new HashSet<>();
    for (var movie : first.path("items")) {
      ids.add(movie.path("id").asLong());
      assertTrue(movie.path("releaseYear").asInt() >= 1990);
      assertTrue(movie.path("releaseYear").asInt() <= 2020);
      assertTrue(movie.path("runtime").asInt() <= 120);
    }
    for (var movie : read(send("GET", query + "&page=2", null, false)).path("items"))
      assertFalse(ids.contains(movie.path("id").asLong()));
    for (String invalid :
        List.of(
            "yearFrom=1800",
            "yearTo=2101",
            "yearFrom=2020&yearTo=1990",
            "maxRuntime=121",
            "genre=invalid")) status(400, send("GET", "/movies?" + invalid, null, false));
  }

  @Test
  void topicsAreValidatedAndOnlyEnabledTopicsArePublic() throws Exception {
    login(newAccount("USER"));
    status(403, send("GET", "/admin/topics", null, false));
    login(newAccount("ADMIN"));
    Map<String, Object> input =
        new HashMap<>(
            Map.of(
                "title",
                "测试专题",
                "description",
                "组合筛选",
                "genre",
                "动画",
                "yearFrom",
                2000,
                "yearTo",
                2025,
                "maxRuntime",
                120,
                "position",
                5,
                "enabled",
                false));
    var response = send("POST", "/admin/topics", input, true);
    status(201, response);
    String id = read(response).path("id").asText();
    assertFalse(send("GET", "/topics", null, false).body().contains(id));
    input.put("enabled", true);
    status(200, send("PUT", "/admin/topics/" + id, input, true));
    assertTrue(send("GET", "/topics", null, false).body().contains(id));
    input.put("yearFrom", 2026);
    status(400, send("PUT", "/admin/topics/" + id, input, true));
    status(200, send("DELETE", "/admin/topics/" + id, null, true));
    assertFalse(send("GET", "/topics", null, false).body().contains(id));
  }

  @Test
  void recommendationObservationRequiresExposureRespectsOwnershipAndOptOut() throws Exception {
    var guest = read(send("GET", "/recommendations", null, false));
    assertTrue(guest.get(0).path("requestId").isNull());
    String owner = newAccount("USER");
    login(owner);
    var picks = read(send("GET", "/recommendations", null, false));
    String requestId = picks.get(0).path("requestId").asText();
    long movie = picks.get(0).path("movie").path("id").asLong();
    Map<String, Object> event =
        Map.of("requestId", requestId, "movieId", movie, "event", "exposure");
    status(403, send("POST", "/me/recommendation-events", event, false));
    login(newAccount("USER"));
    status(404, send("POST", "/me/recommendation-events", event, true));
    login(owner);
    status(200, send("PUT", "/me/library/" + movie, Map.of("status", "WATCHLIST"), true));
    var db = jdbc;
    assertNull(
        db.queryForObject(
            "SELECT saved_at FROM recommendation_observations WHERE run_id=? AND movie_id=?",
            java.sql.Timestamp.class,
            requestId,
            movie));
    status(200, send("POST", "/me/recommendation-events", event, true));
    var exposed =
        db.queryForObject(
            "SELECT exposed_at FROM recommendation_observations WHERE run_id=? AND movie_id=?",
            java.sql.Timestamp.class,
            requestId,
            movie);
    status(200, send("POST", "/me/recommendation-events", event, true));
    assertEquals(
        exposed,
        db.queryForObject(
            "SELECT exposed_at FROM recommendation_observations WHERE run_id=? AND movie_id=?",
            java.sql.Timestamp.class,
            requestId,
            movie));
    status(
        200,
        send(
            "POST",
            "/me/recommendation-events",
            Map.of("requestId", requestId, "movieId", movie, "event", "click"),
            true));
    status(200, send("PUT", "/me/library/" + movie, Map.of("status", "WATCHLIST"), true));
    status(200, send("PUT", "/me/reviews/" + movie, Map.of("stars", 5, "body", "来自真实曝光"), true));
    assertNotNull(
        db.queryForObject(
            "SELECT saved_at FROM recommendation_observations WHERE run_id=? AND movie_id=?",
            java.sql.Timestamp.class,
            requestId,
            movie));
    assertEquals(
        5,
        db.queryForObject(
            "SELECT rating_stars FROM recommendation_observations WHERE run_id=? AND movie_id=?",
            Integer.class,
            requestId,
            movie));
    status(403, send("GET", "/admin/recommendation-metrics", null, false));
    status(200, send("PUT", "/me/recommendation-settings", Map.of("enabled", false), true));
    assertTrue(
        read(send("GET", "/recommendations", null, false)).get(0).path("requestId").isNull());
    status(200, send("DELETE", "/me/recommendation-history", null, true));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM recommendation_runs WHERE user_id=?",
            Long.class,
            accounts.findByUsername(owner).id()));
    assertEquals(5, read(send("GET", "/me/reviews/" + movie, null, false)).path("stars").asInt());
    login(newAccount("ADMIN"));
    status(200, send("GET", "/admin/recommendation-metrics?days=30", null, false));
    status(400, send("GET", "/admin/recommendation-metrics?days=1", null, false));
  }

  @Test
  void retentionAlwaysKeepsTheJustReturnedRequest() throws Exception {
    login(newAccount("USER"));
    long user = read(send("GET", "/session", null, false)).path("user").path("id").asLong();
    // Same timestamp and lexicographically later IDs reproduce the retention boundary.
    for (int i = 0; i < 200; i++)
      jdbc.update(
          "INSERT INTO recommendation_runs(id,user_id,algorithm_version,mode,segment,created_at)"
              + " VALUES(?,?,?,?,?,?)",
          "zz-retention-" + user + "-" + i,
          user,
          "fixture",
          "balanced",
          "new",
          java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(60)));
    var picks = read(send("GET", "/recommendations", null, false));
    String run = picks.get(0).path("requestId").asText();
    assertEquals(
        200L,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM recommendation_runs WHERE user_id=?", Long.class, user));
    status(
        200,
        send(
            "POST",
            "/me/recommendation-events",
            Map.of(
                "requestId",
                run,
                "movieId",
                picks.get(0).path("movie").path("id").asLong(),
                "event",
                "exposure"),
            true));
  }

  @Test
  void observationAttributionExpiresAndRetentionRemovesOldRuns() throws Exception {
    String owner = newAccount("USER");
    login(owner);
    var picks = read(send("GET", "/recommendations", null, false));
    String run = picks.get(0).path("requestId").asText();
    long movie = picks.get(0).path("movie").path("id").asLong();
    var past = java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(8)));
    jdbc.update("UPDATE recommendation_observations SET exposed_at=? WHERE run_id=?", past, run);
    status(200, send("PUT", "/me/reviews/" + movie, Map.of("stars", 4, "body", ""), true));
    assertNull(
        jdbc.queryForObject(
            "SELECT rated_at FROM recommendation_observations WHERE run_id=? AND movie_id=?",
            java.sql.Timestamp.class,
            run,
            movie));
    jdbc.update(
        "UPDATE recommendation_runs SET created_at=? WHERE id=?",
        java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(91))),
        run);
    observation.prune();
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM recommendation_runs WHERE id=?", Long.class, run));
  }

  @Test
  void trainedModelLifecycleAuthorizationAndImmediatePersonalization() throws Exception {
    status(401, send("GET", "/admin/recommender/models", null, false));
    String regular = newAccount("USER");
    login(regular);
    status(403, send("GET", "/admin/recommender/models", null, false));
    String admin = newAccount("ADMIN");
    login(admin);
    status(403, send("POST", "/admin/recommender/disable", null, false));
    status(
        400,
        send(
            "POST",
            "/admin/recommender/models",
            Map.of(
                "dataset",
                "../escape",
                "rank",
                2,
                "iterations",
                2,
                "regularization",
                .1,
                "seed",
                42),
            true));
    List<Long> fixtures = new ArrayList<>();
    List<String> versions = new ArrayList<>();
    var movieIds = jdbc.queryForList("SELECT id FROM movies ORDER BY id LIMIT 8", Long.class);
    try {
      for (int i = 0; i < 5; i++) {
        long id = accounts.findByUsername(newAccount("USER")).id();
        fixtures.add(id);
        for (int j = 0; j < movieIds.size(); j++)
          jdbc.update(
              "INSERT INTO reviews(user_id,movie_id,stars,body) VALUES(?,?,?,?)",
              id,
              movieIds.get(j),
              1 + (i + j) % 5,
              "");
      }
      String first = null;
      for (int version = 0; version < 2; version++) {
        var response =
            send(
                "POST",
                "/admin/recommender/models",
                Map.of(
                    "dataset",
                    "local",
                    "rank",
                    2,
                    "iterations",
                    2,
                    "regularization",
                    .1,
                    "seed",
                    42 + version),
                true);
        status(202, response);
        String id = read(response).path("id").asText();
        versions.add(id);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        String state = "";
        do {
          state =
              jdbc.queryForObject(
                  "SELECT status FROM recommendation_models WHERE id=?", String.class, id);
          if (Set.of("READY", "FAILED").contains(state)) break;
          Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        assertEquals(
            "READY",
            state,
            jdbc.queryForObject(
                "SELECT error_message FROM recommendation_models WHERE id=?", String.class, id));
        status(200, send("POST", "/admin/recommender/models/" + id + "/activate", null, true));
        status(409, send("DELETE", "/admin/recommender/models/" + id, null, true));
        var exported = send("GET", "/admin/recommender/models/" + id + "/export", null, false);
        status(200, exported);
        assertTrue(read(exported).path("artifact").path("factors").size() >= 8);
        assertFalse(read(exported).path("version").path("summary").path("evaluated").asBoolean());
        if (version == 0) first = id;
      }
      // The persisted pointer and artifact can be loaded by a fresh registry after a restart.
      var secondReader =
          new com.cinematch.recommender.ModelRegistry(
              jdbc,
              new com.cinematch.recommender.TrainingData(
                  System.getProperty("java.io.tmpdir") + "/cinematch-model-tests", jdbc),
              new org.springframework.transaction.support.TransactionTemplate(
                  new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                      jdbc.getDataSource())),
              "",
              false);
      assertEquals(versions.get(1), secondReader.active().id());
      status(200, send("POST", "/admin/recommender/models/" + first + "/activate", null, true));
      login(regular);
      assertFalse(
          read(send("GET", "/recommendations/status", null, false)).path("ready").asBoolean());
      var starters = read(send("GET", "/me/recommendations/starters", null, false));
      assertTrue(starters.size() >= 3);
      for (int i = 0; i < 3; i++)
        status(
            200,
            send(
                "PUT",
                "/me/reviews/" + starters.get(i).path("id").asLong(),
                Map.of("stars", i + 3, "body", ""),
                true));
      var ready = read(send("GET", "/recommendations/status", null, false));
      assertTrue(ready.path("ready").asBoolean());
      assertEquals(3, ready.path("mappedRatings").asInt());
      assertFalse(ready.path("tasteGenres").isEmpty());
      var picks = read(send("GET", "/recommendations", null, false));
      assertFalse(picks.isEmpty());
      assertEquals("als-hybrid", picks.get(0).path("engine").asText());
      assertEquals(first, picks.get(0).path("modelVersion").asText());
      status(200, send("DELETE", "/me/reviews/" + starters.get(0).path("id").asLong(), null, true));
      assertFalse(
          read(send("GET", "/recommendations/status", null, false)).path("ready").asBoolean());
      login(admin);
      status(200, send("POST", "/admin/recommender/disable", null, true));
      assertTrue(
          read(send("GET", "/recommendations/status", null, false)).path("modelVersion").isNull());
      for (String id : versions)
        status(200, send("DELETE", "/admin/recommender/models/" + id, null, true));
    } finally {
      jdbc.update("UPDATE recommendation_model_state SET active_model_id=NULL WHERE id=1");
      for (String id : versions) jdbc.update("DELETE FROM recommendation_models WHERE id=?", id);
      fixtures.add(accounts.findByUsername(regular).id());
      fixtures.add(accounts.findByUsername(admin).id());
      for (long id : fixtures) jdbc.update("DELETE FROM users WHERE id=?", id);
    }
  }

  @Test
  void externalTrainingChecksIdentityHashesAndSeparateUserNamespaces() throws Exception {
    var root = java.nio.file.Files.createTempDirectory("cinematch-training-");
    var directory = java.nio.file.Files.createDirectories(root.resolve("datasets/fixture"));
    var movies =
        jdbc.query(
            "SELECT * FROM movies ORDER BY id LIMIT 5",
            (r, n) -> com.cinematch.movie.MovieRows.read(r));
    long siteUser = accounts.findByUsername(newAccount("USER")).id();
    try {
      StringBuilder csv = new StringBuilder("userId,movieId,rating,timestamp\n");
      for (int u = 0; u < 5; u++)
        for (int i = 0; i < 5; i++)
          csv.append(siteUser + u).append(',').append(1001 + i).append(",4,1\n");
      List<com.cinematch.recommender.TrainingData.Mapping> mappings = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        var m = movies.get(i);
        mappings.add(
            new com.cinematch.recommender.TrainingData.Mapping(
                m.id(),
                1001 + i,
                m.title(),
                m.releaseYear(),
                m.sourceUrl(),
                "tt000000" + (i + 1),
                "test-fixture"));
        jdbc.update(
            "INSERT INTO reviews(user_id,movie_id,stars,body) VALUES(?,?,5,'')", siteUser, m.id());
      }
      java.nio.file.Files.writeString(directory.resolve("ratings.csv"), csv);
      java.nio.file.Files.writeString(
          directory.resolve("mappings.json"), json.writeValueAsString(mappings));
      Map<String, String> hashes = new HashMap<>();
      for (String file : List.of("ratings.csv", "mappings.json"))
        hashes.put(
            file,
            HexFormat.of()
                .formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(java.nio.file.Files.readAllBytes(directory.resolve(file)))));
      java.nio.file.Files.writeString(
          directory.resolve("manifest.json"),
          json.writeValueAsString(
              Map.of(
                  "format",
                  "cinematch.ratings.v1",
                  "ratings",
                  25,
                  "users",
                  5,
                  "items",
                  5,
                  "sha256",
                  hashes)));
      var data = new com.cinematch.recommender.TrainingData(root.toString(), jdbc);
      var loaded = data.load("fixture", UUID.randomUUID().toString());
      long siteRows =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM reviews WHERE movie_id IN ("
                  + movies.stream()
                      .map(m -> String.valueOf(m.id()))
                      .collect(java.util.stream.Collectors.joining(","))
                  + ")",
              Long.class);
      assertEquals(25 + siteRows, loaded.ratings().size());
      assertTrue(((Number) loaded.summary().get("users")).intValue() >= 6);
      assertTrue(loaded.ratings().stream().anyMatch(r -> r.user() == siteUser));
      assertEquals(5, loaded.ratings().stream().filter(r -> r.user() == -siteUser).count());
      assertEquals(5, loaded.mappings().size());
      assertEquals(siteRows, ((Number) loaded.summary().get("siteRatings")).longValue());
      assertThrows(
          IllegalArgumentException.class,
          () -> data.load("../fixture", UUID.randomUUID().toString()));
      java.nio.file.Files.writeString(directory.resolve("ratings.csv"), "tampered");
      assertThrows(
          IllegalArgumentException.class, () -> data.load("fixture", UUID.randomUUID().toString()));
      var m = movies.getFirst();
      var incorrect =
          new com.cinematch.recommender.TrainingData.Mapping(
              m.id(), 1001, m.title(), m.releaseYear() + 1, m.sourceUrl(), "tt0000001", "test");
      assertFalse(incorrect.matches(m));
    } finally {
      jdbc.update("DELETE FROM users WHERE id=?", siteUser);
      try (var files = java.nio.file.Files.walk(root)) {
        for (var file : files.sorted(Comparator.reverseOrder()).toList())
          java.nio.file.Files.delete(file);
      }
    }
  }
}

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
      "app.admin.username=",
      "app.admin.password="
    })
class ApiIntegrationTest {
  @LocalServerPort int port;
  @Autowired AccountMapper accounts;
  @Autowired PasswordEncoder passwords;
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
    status(201, send("POST", "/admin/movies", input, true));
    long id =
        read(send("GET", "/movies?q=Integration%20Movie", null, false))
            .path("items")
            .get(0)
            .path("id")
            .asLong();
    input.put("title", "更新的电影");
    status(200, send("PUT", "/admin/movies/" + id, input, true));
    assertEquals("更新的电影", read(send("GET", "/movies/" + id, null, false)).path("title").asText());
    status(200, send("DELETE", "/admin/movies/" + id, null, true));
    status(404, send("GET", "/movies/" + id, null, false));
  }
}

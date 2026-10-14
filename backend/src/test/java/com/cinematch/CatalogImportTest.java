package com.cinematch;

import static org.junit.jupiter.api.Assertions.*;

import com.cinematch.admin.AdminController;
import com.cinematch.catalog.CatalogImportService;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:catalog_import;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.admin.username=",
      "app.admin.password=",
      "app.catalog.import-file="
    })
class CatalogImportTest {
  @Autowired CatalogImportService importer;
  @Autowired JdbcTemplate jdbc;
  @TempDir Path directory;

  CatalogImportService.Entry entry(String id, String title, int year) {
    return new CatalogImportService.Entry(
        id,
        "https://movie.douban.com/subject/" + id + "/",
        "2025",
        new AdminController.Input(
            title,
            "Original " + id,
            year,
            "剧情",
            120,
            8.5,
            12000,
            "Test Director",
            "中国",
            "用于验证导入的虚构资料。",
            "/images/placeholder.svg",
            "/images/placeholder.svg",
            false));
  }

  Path file(CatalogImportService.Entry... rows) throws Exception {
    Path file = directory.resolve(UUID.randomUUID() + ".json");
    Files.writeString(file, JsonMapper.builder().build().writeValueAsString(rows));
    return file;
  }

  long count() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM movies", Long.class);
  }

  @Test
  void importsAreIdempotentAndPreserveExistingUserDataAndFilmIds() throws Exception {
    jdbc.update(
        "INSERT INTO users(username,display_name,password_hash)"
            + " VALUES('catalog_viewer','测试影迷','not-a-login-password')");
    long userId =
        jdbc.queryForObject("SELECT id FROM users WHERE username='catalog_viewer'", Long.class);
    jdbc.update(
        "INSERT INTO library_entries(user_id,movie_id,status) VALUES(?,13,'WATCHED')", userId);
    jdbc.update("INSERT INTO reviews(user_id,movie_id,stars,body) VALUES(?,13,5,'保留这条短评')", userId);
    var input = file(entry("910000001", "肖申克的救赎", 1994), entry("910000002", "导入新电影", 2001));
    var first = importer.importFile(input);
    assertEquals(new CatalogImportService.Result(1, 1, 0), first);
    assertEquals(new CatalogImportService.Result(0, 2, 0), importer.importFile(input));
    assertEquals(
        13L, jdbc.queryForObject("SELECT id FROM movies WHERE source_id='910000001'", Long.class));
    assertEquals(
        "The Shawshank Redemption",
        jdbc.queryForObject("SELECT original_title FROM movies WHERE id=13", String.class));
    assertEquals(
        5,
        jdbc.queryForObject(
            "SELECT stars FROM reviews WHERE user_id=? AND movie_id=13", Integer.class, userId));
    assertEquals(
        "保留这条短评",
        jdbc.queryForObject(
            "SELECT body FROM reviews WHERE user_id=? AND movie_id=13", String.class, userId));
    assertEquals(
        "WATCHED",
        jdbc.queryForObject(
            "SELECT status FROM library_entries WHERE user_id=? AND movie_id=13",
            String.class,
            userId));
    assertEquals(
        "douban", jdbc.queryForObject("SELECT data_source FROM movies WHERE id=13", String.class));
  }

  @Test
  void laterImportsDoNotOverwriteManualEdits() throws Exception {
    var input = file(entry("910000003", "管理员会修改的电影", 2002));
    importer.importFile(input);
    jdbc.update(
        "UPDATE movies SET title='人工修订标题', data_source='manual' WHERE source_id='910000003'");
    assertEquals(new CatalogImportService.Result(0, 0, 1), importer.importFile(input));
    assertEquals(
        "人工修订标题",
        jdbc.queryForObject("SELECT title FROM movies WHERE source_id='910000003'", String.class));
  }

  @Test
  void duplicateSourceIdsAreRejectedBeforeWriting() throws Exception {
    long before = count();
    var input = file(entry("910000004", "重复一", 2003), entry("910000004", "重复二", 2004));
    assertThrows(IllegalArgumentException.class, () -> importer.importFile(input));
    assertEquals(before, count());
  }

  @Test
  void ambiguousMatchingRollsBackEarlierRows() throws Exception {
    for (int i = 0; i < 2; i++) {
      jdbc.update(
          "INSERT INTO"
              + " movies(title,original_title,release_year,genres,runtime,rating,rating_count,director,country,synopsis,poster_url,backdrop_url,featured)"
              + " VALUES('同名歧义','Ambiguous',2005,'剧情',100,8,100,'导演','中国','简介','/images/placeholder.svg','/images/placeholder.svg',false)");
    }
    long before = count();
    var input = file(entry("910000005", "先写入的新电影", 2006), entry("910000006", "同名歧义", 2005));
    assertThrows(IllegalArgumentException.class, () -> importer.importFile(input));
    assertEquals(before, count());
    assertEquals(
        0L,
        jdbc.queryForObject("SELECT COUNT(*) FROM movies WHERE source_id='910000005'", Long.class));
  }
}

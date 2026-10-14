package com.cinematch.movie;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MovieMapper {
  String COLUMNS =
      "id,title,original_title,release_year,genres,runtime,rating,rating_count,director,country,synopsis,poster_url,backdrop_url,featured,data_source,source_id,source_url,source_snapshot";
  String FILTER =
      " WHERE (#{q} = '' OR LOWER(title) LIKE CONCAT('%', LOWER(#{q}), '%') OR"
          + " LOWER(original_title) LIKE CONCAT('%', LOWER(#{q}), '%') OR LOWER(director) LIKE"
          + " CONCAT('%', LOWER(#{q}), '%')) AND (#{genre} = '' OR CONCAT(',', genres, ',') LIKE"
          + " CONCAT('%,', #{genre}, ',%')) AND (#{yearFrom}=0 OR release_year >= #{yearFrom}) AND"
          + " (#{yearTo}=0 OR release_year &lt;= #{yearTo}) AND (#{maxRuntime}=0 OR (runtime>0 AND"
          + " runtime &lt;= #{maxRuntime}))";

  @Select(
      "<script>SELECT "
          + COLUMNS
          + " FROM movies"
          + FILTER
          + "<choose><when test=\"sort == 'rating'\"> ORDER BY rating DESC, rating_count DESC, id"
          + " ASC</when><when test=\"sort == 'recent'\"> ORDER BY release_year DESC, rating DESC,"
          + " id ASC</when><otherwise> ORDER BY rating_count DESC, rating DESC, id"
          + " ASC</otherwise></choose> LIMIT #{size} OFFSET #{offset}</script>")
  List<Movie> searchFiltered(
      @Param("q") String q,
      @Param("genre") String genre,
      @Param("sort") String sort,
      @Param("size") int size,
      @Param("offset") int offset,
      @Param("yearFrom") int yearFrom,
      @Param("yearTo") int yearTo,
      @Param("maxRuntime") int maxRuntime);

  default List<Movie> search(String q, String genre, String sort, int size, int offset) {
    return searchFiltered(q, genre, sort, size, offset, 0, 0, 0);
  }

  @Select("<script>SELECT COUNT(*) FROM movies" + FILTER + "</script>")
  long countFiltered(
      @Param("q") String q,
      @Param("genre") String genre,
      @Param("yearFrom") int yearFrom,
      @Param("yearTo") int yearTo,
      @Param("maxRuntime") int maxRuntime);

  default long count(String q, String genre) {
    return countFiltered(q, genre, 0, 0, 0);
  }

  @Select("SELECT " + COLUMNS + " FROM movies WHERE id = #{id}")
  Movie find(long id);

  @Select(
      "SELECT "
          + COLUMNS
          + " FROM movies ORDER BY featured DESC, rating_count DESC, id ASC LIMIT 5000")
  List<Movie> candidates();

  @Select("SELECT " + COLUMNS + " FROM movies WHERE featured = TRUE ORDER BY id LIMIT 3")
  List<Movie> featured();

  @Insert(
      "INSERT INTO"
          + " movies(title,original_title,release_year,genres,runtime,rating,rating_count,director,country,synopsis,poster_url,backdrop_url,featured)"
          + " VALUES(#{title},#{originalTitle},#{releaseYear},#{genres},#{runtime},#{rating},#{ratingCount},#{director},#{country},#{synopsis},#{posterUrl},#{backdropUrl},#{featured})")
  void insert(Movie movie);

  @Update(
      "UPDATE movies SET"
          + " title=#{title},original_title=#{originalTitle},release_year=#{releaseYear},genres=#{genres},runtime=#{runtime},rating=#{rating},rating_count=#{ratingCount},director=#{director},country=#{country},synopsis=#{synopsis},poster_url=#{posterUrl},backdrop_url=#{backdropUrl},featured=#{featured},"
          + " data_source='manual' WHERE id=#{id}")
  int update(Movie movie);

  @Delete("DELETE FROM movies WHERE id=#{id}")
  int delete(long id);
}

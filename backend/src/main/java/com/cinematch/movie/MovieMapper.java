package com.cinematch.movie;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MovieMapper {
  String COLUMNS =
      "id,title,original_title,release_year,genres,runtime,rating,rating_count,director,country,synopsis,poster_url,backdrop_url,featured";
  String FILTER =
      " WHERE (#{q} = '' OR LOWER(title) LIKE CONCAT('%', LOWER(#{q}), '%') OR"
          + " LOWER(original_title) LIKE CONCAT('%', LOWER(#{q}), '%') OR LOWER(director) LIKE"
          + " CONCAT('%', LOWER(#{q}), '%')) AND (#{genre} = '' OR CONCAT(',', genres, ',') LIKE"
          + " CONCAT('%,', #{genre}, ',%'))";

  @Select(
      "<script>SELECT "
          + COLUMNS
          + " FROM movies"
          + FILTER
          + "<choose><when test=\"sort == 'rating'\"> ORDER BY rating DESC, rating_count DESC, id"
          + " ASC</when><when test=\"sort == 'recent'\"> ORDER BY release_year DESC, rating DESC,"
          + " id ASC</when><otherwise> ORDER BY rating_count DESC, rating DESC, id"
          + " ASC</otherwise></choose> LIMIT #{size} OFFSET #{offset}</script>")
  List<Movie> search(
      @Param("q") String q,
      @Param("genre") String genre,
      @Param("sort") String sort,
      @Param("size") int size,
      @Param("offset") int offset);

  @Select("SELECT COUNT(*) FROM movies" + FILTER)
  long count(@Param("q") String q, @Param("genre") String genre);

  @Select("SELECT " + COLUMNS + " FROM movies WHERE id = #{id}")
  Movie find(long id);

  @Select("SELECT " + COLUMNS + " FROM movies ORDER BY featured DESC, rating_count DESC LIMIT 300")
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
          + " title=#{title},original_title=#{originalTitle},release_year=#{releaseYear},genres=#{genres},runtime=#{runtime},rating=#{rating},rating_count=#{ratingCount},director=#{director},country=#{country},synopsis=#{synopsis},poster_url=#{posterUrl},backdrop_url=#{backdropUrl},featured=#{featured}"
          + " WHERE id=#{id}")
  int update(Movie movie);

  @Delete("DELETE FROM movies WHERE id=#{id}")
  int delete(long id);
}

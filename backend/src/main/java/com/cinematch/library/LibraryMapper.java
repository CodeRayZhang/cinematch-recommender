package com.cinematch.library;

import com.cinematch.movie.Movie;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LibraryMapper {
  record Entry(long movieId, String status) {}

  record Review(long movieId, int stars, String body) {}

  record PublicReview(String displayName, int stars, String body) {}

  @Select(
      "SELECT movie_id, status FROM library_entries WHERE user_id=#{userId} ORDER BY updated_at"
          + " DESC, movie_id DESC")
  List<Entry> entries(long userId);

  @Select(
      "SELECT movie_id, stars, body FROM reviews WHERE user_id=#{userId} ORDER BY updated_at DESC")
  List<Review> reviews(long userId);

  @Select(
      "SELECT u.display_name, r.stars, r.body FROM reviews r JOIN users u ON u.id=r.user_id WHERE"
          + " movie_id=#{movieId} AND r.body != '' ORDER BY r.updated_at DESC LIMIT 20")
  List<PublicReview> publicReviews(long movieId);

  @Select(
      "SELECT "
          + MovieMapperColumns.PREFIXED
          + " FROM movies m JOIN library_entries l ON m.id=l.movie_id WHERE l.user_id=#{userId}"
          + " ORDER BY l.updated_at DESC, m.id DESC")
  List<Movie> movies(long userId);

  @Select(
      "SELECT "
          + MovieMapperColumns.PREFIXED
          + " FROM movies m WHERE EXISTS (SELECT 1 FROM library_entries l WHERE l.movie_id=m.id AND"
          + " l.user_id=#{userId}) OR EXISTS (SELECT 1 FROM reviews r WHERE r.movie_id=m.id AND"
          + " r.user_id=#{userId})")
  List<Movie> preferenceMovies(long userId);

  @Insert(
      "INSERT INTO library_entries(user_id,movie_id,status) VALUES(#{userId},#{movieId},#{status})"
          + " ON DUPLICATE KEY UPDATE status=#{status}, updated_at=CURRENT_TIMESTAMP")
  void save(
      @Param("userId") long userId, @Param("movieId") long movieId, @Param("status") String status);

  @Delete("DELETE FROM library_entries WHERE user_id=#{userId} AND movie_id=#{movieId}")
  void remove(@Param("userId") long userId, @Param("movieId") long movieId);

  @Insert(
      "INSERT INTO reviews(user_id,movie_id,stars,body)"
          + " VALUES(#{userId},#{movieId},#{stars},#{body}) ON DUPLICATE KEY UPDATE stars=#{stars},"
          + " body=#{body}, updated_at=CURRENT_TIMESTAMP")
  void review(
      @Param("userId") long userId,
      @Param("movieId") long movieId,
      @Param("stars") int stars,
      @Param("body") String body);

  class MovieMapperColumns {
    static final String PREFIXED =
        "m.id,m.title,m.original_title,m.release_year,m.genres,m.runtime,m.rating,m.rating_count,m.director,m.country,m.synopsis,m.poster_url,m.backdrop_url,m.featured";
  }
}

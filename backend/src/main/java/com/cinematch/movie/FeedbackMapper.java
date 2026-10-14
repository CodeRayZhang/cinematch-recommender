package com.cinematch.movie;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FeedbackMapper {
  @Select("SELECT movie_id FROM recommendation_feedback WHERE user_id=#{userId}")
  List<Long> hiddenIds(long userId);

  @Select(
      "SELECT "
          + MovieMapper.COLUMNS
          + " FROM movies WHERE id IN (SELECT movie_id FROM recommendation_feedback WHERE"
          + " user_id=#{userId}) ORDER BY title, id")
  List<Movie> hiddenMovies(long userId);

  @Insert(
      "INSERT INTO recommendation_feedback(user_id,movie_id) VALUES(#{userId},#{movieId}) ON"
          + " DUPLICATE KEY UPDATE movie_id=#{movieId}")
  void hide(@Param("userId") long userId, @Param("movieId") long movieId);

  @Delete("DELETE FROM recommendation_feedback WHERE user_id=#{userId} AND movie_id=#{movieId}")
  void restore(@Param("userId") long userId, @Param("movieId") long movieId);
}

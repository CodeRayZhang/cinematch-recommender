package com.cinematch.movie;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared mapping for paged joins without duplicating movie response models. */
public final class MovieRows {
  private MovieRows() {}

  public static Movie read(ResultSet r) throws SQLException {
    return new Movie(
        r.getLong("id"),
        r.getString("title"),
        r.getString("original_title"),
        r.getInt("release_year"),
        r.getString("genres"),
        r.getInt("runtime"),
        r.getDouble("rating"),
        r.getInt("rating_count"),
        r.getString("director"),
        r.getString("country"),
        r.getString("synopsis"),
        r.getString("poster_url"),
        r.getString("backdrop_url"),
        r.getBoolean("featured"),
        r.getString("data_source"),
        r.getString("source_id"),
        r.getString("source_url"),
        r.getString("source_snapshot"));
  }
}

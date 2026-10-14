package com.cinematch.auth;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProfileMapper {
  record Profile(String displayName, String bio, String preferredGenres, LocalDateTime joinedAt) {}

  @Select(
      "SELECT u.display_name, COALESCE(p.bio, '') AS bio, COALESCE(p.preferred_genres, '') AS"
          + " preferred_genres, u.created_at AS joined_at FROM users u LEFT JOIN user_profiles p ON"
          + " u.id=p.user_id WHERE u.id=#{id}")
  Profile find(long id);

  @Update("UPDATE users SET display_name=#{name} WHERE id=#{id}")
  void rename(@Param("id") long id, @Param("name") String name);

  @Insert(
      "INSERT INTO user_profiles(user_id,bio,preferred_genres) VALUES(#{id},#{bio},#{genres}) ON"
          + " DUPLICATE KEY UPDATE bio=#{bio}, preferred_genres=#{genres}")
  void save(@Param("id") long id, @Param("bio") String bio, @Param("genres") String genres);
}

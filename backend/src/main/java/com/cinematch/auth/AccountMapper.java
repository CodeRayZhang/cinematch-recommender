package com.cinematch.auth;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
  @Select(
      "SELECT id, username, display_name, password_hash, role FROM users WHERE username ="
          + " #{username}")
  Account findByUsername(String username);

  @Insert(
      "INSERT INTO users(username, display_name, password_hash, role) VALUES(#{username},"
          + " #{displayName}, #{passwordHash}, #{role})")
  void insert(
      @Param("username") String username,
      @Param("displayName") String displayName,
      @Param("passwordHash") String passwordHash,
      @Param("role") String role);
}

package com.cinematch.auth;

public record Account(
    long id, String username, String displayName, String passwordHash, String role) {
  public PublicAccount publicView() {
    return new PublicAccount(id, username, displayName, role);
  }

  public record PublicAccount(long id, String username, String displayName, String role) {}
}

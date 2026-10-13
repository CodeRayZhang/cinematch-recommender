package com.cinematch.config;

import com.cinematch.auth.AccountMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
  @Bean
  ApplicationRunner bootstrapAdmin(
      AccountMapper accounts,
      PasswordEncoder encoder,
      @Value("${app.admin.username}") String username,
      @Value("${app.admin.password}") String password) {
    return args -> {
      if (username.isBlank() && password.isBlank()) return;
      if (!username.matches("[a-z0-9_]{3,32}")
          || password.length() < 12
          || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
        throw new IllegalArgumentException("管理员账号需为 3–32 位小写字母、数字或下划线；密码需至少 12 个字符且不超过 72 字节");
      if (accounts.findByUsername(username) == null)
        accounts.insert(username, "策展人", encoder.encode(password), "ADMIN");
    };
  }
}

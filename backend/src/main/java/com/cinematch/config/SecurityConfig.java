package com.cinematch.config;

import com.cinematch.auth.AccountMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService userDetailsService(AccountMapper accounts) {
    return username -> {
      var account = accounts.findByUsername(username);
      if (account == null) throw new UsernameNotFoundException("账号或密码不正确");
      return User.withUsername(account.username())
          .password(account.passwordHash())
          .roles(account.role())
          .build();
    };
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/me/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/**")
                    .permitAll()
                    .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .denyAll()
                    .anyRequest()
                    .permitAll())
        .formLogin(
            form ->
                form.loginProcessingUrl("/api/auth/login")
                    .successHandler((req, res, auth) -> write(res, 200, "登录成功"))
                    .failureHandler((req, res, ex) -> write(res, 401, "账号或密码不正确")))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                    .logoutSuccessHandler((req, res, auth) -> write(res, 200, "已退出登录")))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint((req, res, ex) -> write(res, 401, "请先登录"))
                    .accessDeniedHandler((req, res, ex) -> write(res, 403, "请求已失效或没有访问权限，请刷新后重试")))
        .build();
  }

  private static void write(HttpServletResponse response, int status, String message)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"message\":\"" + message + "\"}");
  }
}

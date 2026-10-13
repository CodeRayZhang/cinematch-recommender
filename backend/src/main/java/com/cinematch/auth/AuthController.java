package com.cinematch.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AccountMapper accounts;
  private final PasswordEncoder encoder;

  public AuthController(AccountMapper accounts, PasswordEncoder encoder) {
    this.accounts = accounts;
    this.encoder = encoder;
  }

  @GetMapping("/session")
  public Session session(Authentication authentication, CsrfToken csrf) {
    var account = authentication == null ? null : accounts.findByUsername(authentication.getName());
    return new Session(account == null ? null : account.publicView(), csrf.getToken());
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Account.PublicAccount register(@Valid @RequestBody Registration input) {
    String username = input.username().toLowerCase(java.util.Locale.ROOT);
    if (accounts.findByUsername(username) != null)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "这个账号已被使用");
    if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能超过 72 字节");
    accounts.insert(
        username, input.displayName().strip(), encoder.encode(input.password()), "USER");
    return accounts.findByUsername(username).publicView();
  }

  public record Session(Account.PublicAccount user, String csrfToken) {}

  public record Registration(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,32}", message = "账号需为 3–32 位字母、数字或下划线")
          String username,
      @NotBlank @Size(max = 40) String displayName,
      @NotNull @Size(min = 8, max = 72, message = "密码需为 8–72 个字符") String password) {}
}

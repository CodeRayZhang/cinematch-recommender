package com.cinematch.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me/profile")
public class ProfileController {
  public static final Set<String> GENRES =
      Set.of(
          "剧情", "喜剧", "爱情", "科幻", "动画", "动作", "冒险", "悬疑", "惊悚", "恐怖", "犯罪", "奇幻", "家庭", "音乐", "歌舞",
          "传记", "历史", "战争", "运动", "西部", "纪录片", "武侠", "古装");
  private final AccountMapper accounts;
  private final ProfileMapper profiles;

  public ProfileController(AccountMapper accounts, ProfileMapper profiles) {
    this.accounts = accounts;
    this.profiles = profiles;
  }

  @GetMapping
  public ProfileMapper.Profile get(Principal principal) {
    return profiles.find(accounts.findByUsername(principal.getName()).id());
  }

  @PutMapping
  @Transactional
  public ProfileMapper.Profile save(Principal principal, @Valid @RequestBody Input input) {
    if (!GENRES.containsAll(input.preferredGenres()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的电影类型");
    long id = accounts.findByUsername(principal.getName()).id();
    profiles.rename(id, input.displayName().strip());
    profiles.save(
        id, input.bio().strip(), String.join(",", new LinkedHashSet<>(input.preferredGenres())));
    return profiles.find(id);
  }

  public record Input(
      @NotBlank @Size(max = 40) String displayName,
      @NotNull @Size(max = 280) String bio,
      @NotNull @Size(max = 8) List<@NotBlank String> preferredGenres) {}
}

package com.cinematch.movie;

import com.cinematch.auth.AccountMapper;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/feedback")
public class FeedbackController {
  private final AccountMapper accounts;
  private final FeedbackMapper feedback;
  private final MovieMapper movies;

  public FeedbackController(AccountMapper accounts, FeedbackMapper feedback, MovieMapper movies) {
    this.accounts = accounts;
    this.feedback = feedback;
    this.movies = movies;
  }

  @GetMapping
  public List<Movie> get(Principal p) {
    return feedback.hiddenMovies(accounts.findByUsername(p.getName()).id());
  }

  @PutMapping("/{movieId}")
  public Map<String, String> hide(Principal p, @PathVariable long movieId) {
    MovieController.require(movies, movieId);
    feedback.hide(accounts.findByUsername(p.getName()).id(), movieId);
    return Map.of("message", "将不再推荐这部电影，可在个人中心恢复");
  }

  @DeleteMapping("/{movieId}")
  public Map<String, String> restore(Principal p, @PathVariable long movieId) {
    feedback.restore(accounts.findByUsername(p.getName()).id(), movieId);
    return Map.of("message", "已恢复推荐");
  }
}

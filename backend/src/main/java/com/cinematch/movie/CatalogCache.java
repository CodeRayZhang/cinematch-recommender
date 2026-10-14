package com.cinematch.movie;

import java.time.Duration;
import java.util.List;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

/** One bounded public catalog snapshot. Personal recommendations are never cached. */
@Component
public class CatalogCache {
  public record Changed() {}

  private final MovieMapper movies;
  private final LongSupplier clock;
  private final long ttl;
  private List<Movie> cached;
  private long expires;

  @Autowired
  public CatalogCache(MovieMapper movies) {
    this(movies, System::nanoTime, Duration.ofMinutes(5).toNanos());
  }

  CatalogCache(MovieMapper movies, LongSupplier clock, long ttl) {
    this.movies = movies;
    this.clock = clock;
    this.ttl = ttl;
  }

  public synchronized List<Movie> candidates() {
    long now = clock.getAsLong();
    if (cached == null || now >= expires) {
      cached = List.copyOf(movies.candidates());
      expires = now + ttl;
    }
    return cached;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public synchronized void invalidate(Changed event) {
    cached = null;
  }
}

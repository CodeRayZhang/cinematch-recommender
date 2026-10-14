package com.cinematch.movie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CatalogCacheTest {
  @Test
  void cachesOnlyCatalogAndReloadsOnExpiryOrCommittedMutation() {
    var mapper = mock(MovieMapper.class);
    var clock = new AtomicLong(0);
    when(mapper.candidates()).thenReturn(List.of());
    var cache = new CatalogCache(mapper, clock::get, 100);
    assertSame(cache.candidates(), cache.candidates());
    verify(mapper, times(1)).candidates();
    clock.set(101);
    cache.candidates();
    verify(mapper, times(2)).candidates();
    cache.invalidate(new CatalogCache.Changed());
    cache.candidates();
    verify(mapper, times(3)).candidates();
  }
}

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import {
  user,
  library,
  authOpen,
  loadLibrary,
  movieStatus,
  requireUser,
  notify,
} from "../state";
import type { Movie } from "../types";
import MovieCard from "../components/MovieCard.vue";
import Icon from "../components/Icon.vue";
import CollectionPicker from "../components/CollectionPicker.vue";
const collectionMovies = ref<number[]>([]);
import { ratingLabel, sourceNote } from "../movieSource";
const route = useRoute();
const movie = ref<Movie>(),
  related = ref<Movie[]>([]),
  reviews = ref<{ displayName: string; stars: number; body: string }[]>([]);
const loading = ref(true),
  error = ref(""),
  stars = ref(0),
  body = ref(""),
  submitting = ref(false),
  saving = ref(false);
const removed = ref<"WATCHLIST" | "WATCHED" | null>(null);
const status = computed(() => movie.value && movieStatus(movie.value.id));
const starsText = [
  "点击星星，留下你的评分",
  "不太喜欢",
  "有些遗憾",
  "还不错",
  "很喜欢",
  "值得珍藏",
];
let controller: AbortController | undefined;
async function load() {
  controller?.abort();
  const current = new AbortController();
  controller = current;
  loading.value = true;
  error.value = "";
  movie.value = undefined;
  removed.value = null;
  try {
    const id = route.params.id;
    const result = await Promise.all([
      api<Movie>(`/movies/${id}`, { signal: current.signal }),
      api<Movie[]>(`/movies/${id}/related`, { signal: current.signal }),
      api<typeof reviews.value>(`/movies/${id}/reviews`, {
        signal: current.signal,
      }),
    ]);
    [movie.value, related.value, reviews.value] = result;
    document.title = `${movie.value.title} · CineMatch`;
  } catch (e) {
    if ((e as Error).name !== "AbortError") error.value = (e as Error).message;
  } finally {
    if (!current.signal.aborted) loading.value = false;
  }
}
watch(() => route.params.id, load, { immediate: true });
watch(
  [() => route.params.id, () => library.reviews],
  async () => {
    const r = library.reviews.find(
      (x) => x.movieId === Number(route.params.id),
    );
    stars.value = r?.stars || 0;
    body.value = r?.body || "";
    if (user.value) {
      const id = Number(route.params.id);
      try {
        const own = await api<{ stars: number; body: string }>(
          `/me/reviews/${id}`,
        );
        if (id === Number(route.params.id) && user.value) {
          stars.value = own.stars;
          body.value = own.body;
        }
      } catch (e) {
        if ((e as Error).name !== "AbortError") notify((e as Error).message);
      }
    }
  },
  { immediate: true },
);
watch(
  () => user.value?.id,
  () => (removed.value = null),
);
async function setStatus(next: "WATCHLIST" | "WATCHED" | null) {
  if (!movie.value || saving.value || submitting.value || !requireUser())
    return;
  const id = movie.value.id,
    owner = user.value?.id,
    previous = movieStatus(id);
  saving.value = true;
  try {
    await api(`/me/library/${id}`, {
      method: next ? "PUT" : "DELETE",
      ...(next ? { body: JSON.stringify({ status: next }) } : {}),
    });
    await loadLibrary();
    if (movie.value?.id === id && user.value?.id === owner)
      removed.value = next ? null : previous || null;
    notify(
      next === "WATCHED"
        ? "已标记看过，可以继续留下评分"
        : next === "WATCHLIST"
          ? "已加入想看"
          : "已移出片单，评分和短评保留",
    );
  } catch (e) {
    notify((e as Error).message);
  } finally {
    saving.value = false;
  }
}
async function submitReview() {
  if (!movie.value || !stars.value || saving.value || submitting.value) return;
  submitting.value = true;
  try {
    await api(`/me/reviews/${movie.value.id}`, {
      method: "PUT",
      body: JSON.stringify({ stars: stars.value, body: body.value }),
    });
    await loadLibrary();
    removed.value = null;
    reviews.value = await api(`/movies/${movie.value.id}/reviews`);
    notify("评分已保存，也为你标记了「看过」");
  } catch (e) {
    notify((e as Error).message);
  } finally {
    submitting.value = false;
  }
}
</script>
<template>
  <div v-if="loading" class="page-container empty-state" aria-busy="true">
    <Icon name="LoaderCircle" :size="32" class="spinning" />
    <p>正在拉开银幕…</p>
  </div>
  <div
    v-else-if="error || !movie"
    class="page-container empty-state"
    role="alert"
  >
    <Icon name="Film" :size="40" />
    <h1>这一幕暂时无法呈现</h1>
    <p>{{ error }}</p>
    <RouterLink to="/" class="button primary">回到电影世界</RouterLink>
  </div>
  <div v-else class="detail-page">
    <div class="detail-backdrop">
      <img :src="movie.backdropUrl" :alt="movie.title" />
      <div></div>
    </div>
    <div class="page-container detail-container">
      <RouterLink to="/" class="back-link"
        ><Icon name="ArrowLeft" :size="17" />回到发现</RouterLink
      >
      <div class="detail-layout">
        <aside class="detail-poster">
          <img
            :src="movie.posterUrl"
            :alt="`${movie.title}海报`"
            @error="
              ($event.target as HTMLImageElement).src =
                '/images/placeholder.svg'
            "
          />
          <div
            class="detail-status-controls"
            role="group"
            aria-label="电影片单状态"
          >
            <button
              class="button secondary"
              @click="requireUser() && (collectionMovies = [movie.id])"
            >
              收进自定义片单
            </button>
            <button
              class="button full-width"
              :class="status === 'WATCHLIST' ? 'primary' : 'secondary'"
              :aria-pressed="status === 'WATCHLIST'"
              :disabled="saving || submitting"
              @click="setStatus('WATCHLIST')"
            >
              <Icon name="Bookmark" :size="17" />{{
                status === "WATCHLIST" ? "已加入想看" : "想看"
              }}
            </button>
            <button
              class="button full-width"
              :class="status === 'WATCHED' ? 'primary' : 'secondary'"
              :aria-pressed="status === 'WATCHED'"
              :disabled="saving || submitting"
              @click="setStatus('WATCHED')"
            >
              <Icon name="Check" :size="17" />{{
                status === "WATCHED" ? "已看过" : "看过"
              }}
            </button>
            <button
              v-if="status"
              class="text-link detail-remove"
              :disabled="saving || submitting"
              @click="setStatus(null)"
            >
              <Icon name="Trash2" :size="13" />移出片单
            </button>
            <button
              v-if="removed"
              class="text-link"
              :disabled="saving || submitting"
              @click="setStatus(removed)"
            >
              撤销移出片单
            </button>
          </div>
          <p class="poster-caption">好电影，值得留在你的片单里。</p>
        </aside>
        <div class="detail-content">
          <p class="eyebrow">
            CINEMATCH CINEMA <span class="accent">/</span>
            {{ movie.releaseYear }}
          </p>
          <h1>{{ movie.title }}</h1>
          <p class="original-title">{{ movie.originalTitle }}</p>
          <div class="detail-tags">
            <span v-for="genre in movie.genres.split(',')" :key="genre">{{
              genre
            }}</span
            ><span class="plain-tag">{{ movie.runtime }} 分钟</span
            ><span class="plain-tag">{{ movie.country }}</span>
          </div>
          <div class="detail-rating">
            <strong
              ><Icon name="Star" :size="22" />{{
                movie.rating.toFixed(1)
              }}</strong
            ><span
              >{{ ratingLabel(movie)
              }}<small>{{ sourceNote(movie) }}</small></span
            >
            <div class="director">
              <small>导演</small><span>{{ movie.director }}</span>
            </div>
          </div>
          <section class="synopsis">
            <h2>故事，从这里开始</h2>
            <p>{{ movie.synopsis }}</p>
            <a
              v-if="movie.sourceUrl"
              :href="movie.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="source-link"
              >查看原始电影资料 ↗</a
            >
          </section>
          <section class="review-panel">
            <div class="section-heading">
              <div>
                <p class="eyebrow">LEAVE A LITTLE OF YOURSELF</p>
                <h2>看完之后，你的感受是？</h2>
              </div>
              <Icon name="Clapperboard" :size="28" />
            </div>
            <form v-if="user" @submit.prevent="submitReview">
              <div class="star-picker" role="group" aria-label="我的电影评分">
                <button
                  v-for="n in 5"
                  :key="n"
                  type="button"
                  :class="{ filled: n <= stars }"
                  :aria-label="`${n} 星`"
                  :aria-pressed="stars === n"
                  @click="stars = n"
                >
                  <Icon name="Star" :size="29" /></button
                ><span>{{ starsText[stars] }}</span>
              </div>
              <label class="sr-only" for="review-body">我的短评</label
              ><textarea
                id="review-body"
                v-model="body"
                maxlength="2000"
                rows="3"
                placeholder="哪一个瞬间，留在了你的心里？（短评选填）"
              ></textarea>
              <div class="review-form-bottom">
                <span>{{ body.length }} / 2000</span
                ><button
                  class="button primary"
                  :disabled="!stars || submitting || saving"
                >
                  {{ submitting ? "保存中…" : "保存观影记录"
                  }}<Icon name="ArrowRight" :size="16" />
                </button>
              </div>
            </form>
            <div v-else class="review-login">
              <p>那些被电影触动的瞬间，值得被记录。</p>
              <button class="button secondary" @click="authOpen = true">
                登录，写下我的感受<Icon name="ArrowUpRight" :size="16" />
              </button>
            </div>
          </section>
          <section v-if="reviews.length" class="community-reviews">
            <h2>
              银幕之外的回声
              <span class="muted small">{{ reviews.length }} 条短评</span>
            </h2>
            <article v-for="(review, i) in reviews" :key="i">
              <div>
                <span class="review-avatar">{{
                  review.displayName.slice(0, 1)
                }}</span
                ><strong>{{ review.displayName }}</strong
                ><span class="review-stars"
                  >{{ "★".repeat(review.stars)
                  }}{{ "☆".repeat(5 - review.stars) }}</span
                >
              </div>
              <p>{{ review.body }}</p>
            </article>
          </section>
        </div>
      </div>
      <section v-if="related.length" class="related-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">THE STORY CONTINUES</p>
            <h2>也许，你还会喜欢。</h2>
          </div>
          <RouterLink to="/picks" class="text-link"
            >更多推荐<Icon name="ArrowUpRight" :size="17"
          /></RouterLink>
        </div>
        <div class="movie-grid">
          <MovieCard v-for="m in related" :key="m.id" :movie="m" />
        </div>
      </section>
    </div>
  </div>
  <CollectionPicker
    :movie-ids="collectionMovies"
    @close="collectionMovies = []"
  />
</template>

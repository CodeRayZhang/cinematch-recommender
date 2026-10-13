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
  toggleWatchlist,
  notify,
} from "../state";
import type { Movie } from "../types";
import MovieCard from "../components/MovieCard.vue";
import Icon from "../components/Icon.vue";
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
  () => {
    const r = library.reviews.find(
      (x) => x.movieId === Number(route.params.id),
    );
    stars.value = r?.stars || 0;
    body.value = r?.body || "";
  },
  { immediate: true },
);
async function save() {
  if (!movie.value) return;
  saving.value = true;
  try {
    await toggleWatchlist(movie.value.id);
  } catch (e) {
    notify((e as Error).message);
  } finally {
    saving.value = false;
  }
}
async function submitReview() {
  if (!movie.value || !stars.value) return;
  submitting.value = true;
  try {
    await api(`/me/reviews/${movie.value.id}`, {
      method: "PUT",
      body: JSON.stringify({ stars: stars.value, body: body.value }),
    });
    await loadLibrary();
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
          /><button
            class="button full-width"
            :class="status ? 'secondary' : 'primary'"
            :disabled="saving"
            @click="save"
          >
            <Icon :name="status ? 'Check' : 'Plus'" :size="18" />{{
              status === "WATCHED"
                ? "已看过 · 移出片单"
                : status
                  ? "已想看 · 移出片单"
                  : "加入想看片单"
            }}
          </button>
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
            ><span>策展参考评分<small>演示资料，非实时观众评分</small></span>
            <div class="director">
              <small>导演</small><span>{{ movie.director }}</span>
            </div>
          </div>
          <section class="synopsis">
            <h2>故事，从这里开始</h2>
            <p>{{ movie.synopsis }}</p>
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
                  :disabled="!stars || submitting"
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
</template>

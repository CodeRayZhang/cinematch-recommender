<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import type { Movie, MoviePage } from "../types";
import { movieStatus, toggleWatchlist, notify } from "../state";
import MovieCard from "../components/MovieCard.vue";
import Icon from "../components/Icon.vue";
const route = useRoute(),
  router = useRouter();
const featured = ref<Movie | null>(null),
  data = ref<MoviePage>(),
  loading = ref(true),
  error = ref(""),
  saving = ref(false);
const genres = [
  "全部",
  "科幻",
  "剧情",
  "爱情",
  "动画",
  "悬疑",
  "喜剧",
  "冒险",
  "音乐",
];
const genre = computed(() => String(route.query.genre || ""));
const sort = computed(() => String(route.query.sort || "popular"));
const q = computed(() => String(route.query.q || ""));
const page = computed(() => Math.max(1, Number(route.query.page) || 1));
const pageCount = computed(() => Math.ceil((data.value?.total || 0) / 12));
let controller: AbortController | undefined;
async function load() {
  controller?.abort();
  const current = new AbortController();
  controller = current;
  loading.value = true;
  error.value = "";
  try {
    data.value = await api<MoviePage>(
      `/movies?${new URLSearchParams({ q: q.value, genre: genre.value, sort: sort.value, page: String(page.value), size: "12" })}`,
      { signal: current.signal },
    );
  } catch (e) {
    if ((e as Error).name !== "AbortError") error.value = (e as Error).message;
  } finally {
    if (!current.signal.aborted) loading.value = false;
  }
}
function filter(key: string, value: string) {
  const query = { ...route.query, [key]: value || undefined };
  if (key !== "page") delete query.page;
  router.push({ query });
}
async function saveHero() {
  if (!featured.value) return;
  saving.value = true;
  try {
    await toggleWatchlist(featured.value.id);
  } catch (e) {
    notify((e as Error).message);
  } finally {
    saving.value = false;
  }
}
watch(() => route.fullPath, load, { immediate: true });
onMounted(async () => {
  try {
    featured.value = (await api<Movie[]>("/movies/featured"))[0] || null;
  } catch {
    /* Catalog request owns the visible connection error. */
  }
});
</script>
<template>
  <div class="page-container discover-page">
    <div class="page-intro">
      <p class="eyebrow">
        <span class="live-dot"></span> A LITTLE ESCAPE FROM THE EVERYDAY
      </p>
      <span class="intro-note">给生活，留两个小时。</span>
    </div>
    <section
      v-if="featured && !q && !genre && page === 1"
      class="hero"
      aria-label="本期甄选"
    >
      <img
        class="hero-image"
        :src="featured.backdropUrl"
        :alt="`${featured.title}电影剧照`"
        fetchpriority="high"
      />
      <div class="hero-gradient"></div>
      <div class="hero-content">
        <span class="hero-label"
          ><span></span> 本期甄选 <span class="hero-label-line"></span> EDITOR’S
          PICK</span
        >
        <h1 v-if="featured.id === 1">有些答案，<br />藏在星辰之间。</h1>
        <h1 v-else>{{ featured.title }}</h1>
        <div class="hero-movie-line">
          <strong>{{ featured.title }}</strong
          ><span
            >{{ featured.originalTitle }} · {{ featured.releaseYear }}</span
          >
        </div>
        <p v-if="featured.id === 1" class="hero-description">
          跨越时间与宇宙的尽头，<br />去寻找那个始终让你牵挂的人。
        </p>
        <p v-else class="hero-description">
          {{ featured.synopsis.slice(0, 64)
          }}{{ featured.synopsis.length > 64 ? "…" : "" }}
        </p>
        <div class="hero-buttons">
          <RouterLink :to="`/movie/${featured.id}`" class="button primary"
            >探索这部电影<Icon name="ArrowUpRight" :size="18" /></RouterLink
          ><button class="button glass" :disabled="saving" @click="saveHero">
            <Icon
              :name="movieStatus(featured.id) ? 'Check' : 'Plus'"
              :size="18"
            />{{ movieStatus(featured.id) ? "已在片单" : "加入想看" }}
          </button>
        </div>
      </div>
      <div class="hero-side-caption">
        <span>THE WORLD IS BIGGER<br />THAN YOU THINK.</span
        ><span>{{
          featured.id === 1 ? "01 — 深空来信" : "01 — 编辑甄选"
        }}</span>
      </div>
      <div class="hero-bottom">
        <span
          ><Icon name="Star" :size="14" />{{ featured.rating.toFixed(1) }}<i></i
          >{{ featured.runtime }} 分钟<i></i
          >{{ featured.genres.split(",").join(" / ") }}</span
        ><span class="hero-number">CINEMATCH SELECTS <b>001</b></span>
      </div>
    </section>

    <section class="catalog-section" aria-label="电影片库">
      <div class="section-heading">
        <div>
          <p class="eyebrow">FIND YOUR NEXT FAVORITE</p>
          <h2>
            {{ q ? `关于「${q}」的电影` : "下一部，看什么？"
            }}<span class="heading-dot" aria-hidden="true">•</span>
          </h2>
        </div>
        <label class="sort-control"
          ><Icon name="SlidersHorizontal" :size="16" /><select
            :value="sort"
            aria-label="电影排序"
            @change="filter('sort', ($event.target as HTMLSelectElement).value)"
          >
            <option value="popular">热门优先</option>
            <option value="rating">评分最高</option>
            <option value="recent">上映最近</option>
          </select></label
        >
      </div>
      <div class="filter-row">
        <div class="genre-tabs" aria-label="电影类型">
          <button
            v-for="g in genres"
            :key="g"
            :class="{ active: genre === (g === '全部' ? '' : g) }"
            :aria-pressed="genre === (g === '全部' ? '' : g)"
            @click="filter('genre', g === '全部' ? '' : g)"
          >
            {{ g }}
          </button>
        </div>
        <span v-if="data" class="catalog-count">{{ data.total }} 部好电影</span>
      </div>
      <div v-if="error" class="empty-state" role="alert">
        <Icon name="Film" :size="36" />
        <h3>银幕暂时休息一下</h3>
        <p>{{ error }}</p>
        <button class="button secondary" @click="load">重新加载</button>
      </div>
      <div
        v-else-if="loading"
        class="movie-grid"
        aria-label="正在加载电影"
        aria-busy="true"
      >
        <div v-for="i in 6" :key="i" class="skeleton-card">
          <div></div>
          <span></span><small></small>
        </div>
      </div>
      <div v-else-if="!data?.items.length" class="empty-state">
        <Icon name="Search" :size="36" />
        <h3>还没找到这部电影</h3>
        <p>试试其他片名、导演，或者换一种类型。</p>
        <RouterLink to="/" class="button secondary">浏览全部电影</RouterLink>
      </div>
      <div v-else class="movie-grid">
        <MovieCard
          v-for="(movie, i) in data.items"
          :key="movie.id"
          :movie="movie"
          :index="i"
        />
      </div>
      <div v-if="pageCount > 1 && !error" class="pagination">
        <span>每一页，都有新的相遇。</span>
        <div>
          <button
            class="icon-button"
            :disabled="page <= 1"
            aria-label="上一页"
            @click="filter('page', String(page - 1))"
          >
            <Icon name="ChevronLeft" /></button
          ><button
            v-for="p in pageCount"
            :key="p"
            class="page-button"
            :class="{ active: p === page }"
            :aria-label="`第 ${p} 页`"
            :aria-current="p === page ? 'page' : undefined"
            @click="filter('page', String(p))"
          >
            {{ p }}</button
          ><button
            class="icon-button"
            :disabled="page >= pageCount"
            aria-label="下一页"
            @click="filter('page', String(page + 1))"
          >
            <Icon name="ChevronRight" />
          </button>
        </div>
      </div>
    </section>
    <section v-if="!q && !genre" class="editorial-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">FOLLOW YOUR MOOD</p>
          <h2>换一种心情，打开电影。</h2>
        </div>
        <span class="muted small">今晚，想去哪里？</span>
      </div>
      <div class="mood-grid">
        <RouterLink
          :to="{ path: '/', query: { genre: '科幻' } }"
          class="mood-card cosmic"
          ><img
            src="/images/interstellar-backdrop.jpg"
            alt="遥远的星球"
            loading="lazy" />
          <div>
            <span class="eyebrow">BEYOND THE ORDINARY</span>
            <h3>暂时离开地球</h3>
            <p>把日常调成飞行模式。</p>
          </div>
          <Icon name="ArrowUpRight" :size="28" /></RouterLink
        ><RouterLink
          :to="{ path: '/', query: { genre: '爱情' } }"
          class="mood-card romantic"
          ><div class="mood-art">
            <img src="/images/poster-4.jpg" alt="爱乐之城" loading="lazy" /><img
              src="/images/poster-14.jpg"
              alt="天使爱美丽"
              loading="lazy"
            />
          </div>
          <div>
            <span class="eyebrow">A SOFTER KIND OF EVENING</span>
            <h3>给心情放个假</h3>
            <p>在别人的故事里，遇见温柔。</p>
          </div>
          <Icon name="ArrowUpRight" :size="28"
        /></RouterLink>
      </div>
    </section>
  </div>
</template>

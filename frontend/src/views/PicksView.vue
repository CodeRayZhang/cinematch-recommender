<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import { user, ready, library, authOpen, notify } from "../state";
import type { Pick } from "../types";
import { MOVIE_GENRES } from "../genres";
import Icon from "../components/Icon.vue";
import MovieCard from "../components/MovieCard.vue";
const route = useRoute(),
  router = useRouter();
const mode = computed(() =>
  route.query.mode === "explore" ? "explore" : "balanced",
);
const selectedGenres = computed(() =>
  [
    ...new Set(
      String(route.query.genres || "")
        .split(",")
        .filter((g) => MOVIE_GENRES.includes(g)),
    ),
  ].slice(0, 3),
);
const extraGenres = computed(() =>
  selectedGenres.value.filter((g) => !MOVIE_GENRES.slice(0, 8).includes(g)),
);
const maxRuntime = computed(() =>
  [90, 120, 180].includes(Number(route.query.maxRuntime))
    ? Number(route.query.maxRuntime)
    : 0,
);
const batches = ref<Pick[][]>([]),
  batch = ref(0),
  error = ref(""),
  loading = ref(false),
  exhausted = ref(false),
  expanded = ref(false),
  dismissing = ref<number | null>(null);
const picks = computed(() => batches.value[batch.value] || []);
const seen = computed(() => [
  ...new Set(batches.value.flatMap((group) => group.map((p) => p.movie.id))),
]);
const preferenceCount = computed(
  () =>
    new Set([
      ...library.entries.map((e) => e.movieId),
      ...library.reviews.map((r) => r.movieId),
    ]).size,
);
const canNext = computed(
  () =>
    !loading.value &&
    (batch.value < batches.value.length - 1 ||
      (!exhausted.value && seen.value.length < 120)),
);
let controller: AbortController | undefined;
async function load(reset = false) {
  controller?.abort();
  const request = new AbortController();
  controller = request;
  if (reset) {
    batches.value = [];
    batch.value = 0;
    exhausted.value = false;
  }
  loading.value = true;
  error.value = "";
  try {
    const params = new URLSearchParams({
      mode: mode.value,
      genres: selectedGenres.value.join(","),
      maxRuntime: String(maxRuntime.value),
      exclude: seen.value.join(","),
    });
    const result = await api<Pick[]>(`/recommendations?${params}`, {
      signal: request.signal,
    });
    if (request.signal.aborted) return;
    if (result.length) {
      batches.value.push(result);
      batch.value = batches.value.length - 1;
    } else exhausted.value = true;
  } catch (e) {
    if (!request.signal.aborted) error.value = (e as Error).message;
  } finally {
    if (!request.signal.aborted) loading.value = false;
  }
}
function next() {
  if (batch.value < batches.value.length - 1) batch.value++;
  else if (canNext.value) void load();
}
function filter(key: string, value: string) {
  void router.replace({ query: { ...route.query, [key]: value || undefined } });
}
function toggleGenre(genre: string) {
  const value = selectedGenres.value.includes(genre)
    ? selectedGenres.value.filter((g) => g !== genre)
    : [...selectedGenres.value, genre].slice(0, 3);
  filter("genres", value.join(","));
}
function clearFilters() {
  void router.replace({
    query: { mode: mode.value === "explore" ? "explore" : undefined },
  });
}
async function dismiss(id: number) {
  dismissing.value = id;
  try {
    await api(`/me/feedback/${id}`, { method: "PUT" });
    notify("已隐藏，可在个人中心恢复推荐");
    await load(true);
  } catch (e) {
    notify((e as Error).message);
  } finally {
    dismissing.value = null;
  }
}
watch(
  [
    ready,
    () => user.value?.id,
    () => route.fullPath,
    () => library.entries,
    () => library.reviews,
  ],
  () => {
    if (ready.value) void load(true);
  },
  { immediate: true },
);
onUnmounted(() => controller?.abort());
</script>
<template>
  <div class="page-container inner-page picks-page">
    <section class="personal-intro">
      <div>
        <p class="eyebrow">
          <Icon name="Sparkles" :size="15" /> A CINEMA THAT KNOWS YOU
        </p>
        <h1>
          熟悉的心动，<br /><span class="serif-accent">也来点意外。</span>
        </h1>
        <p>从你的口味出发，给没见过的故事留个位置。</p>
      </div>
      <span class="orbital-art" aria-hidden="true"
        ><i /><i /><Icon name="Sparkles" :size="30"
      /></span>
    </section>
    <section class="recommendation-controls" aria-label="本次推荐设置">
      <div class="recommendation-control-top">
        <div>
          <p class="eyebrow">SET THE MOOD</p>
          <h2>
            {{
              preferenceCount
                ? "今晚，想看一点什么？"
                : "先选口味，找到第一部好电影。"
            }}
          </h2>
        </div>
        <div class="recommendation-modes" role="group" aria-label="推荐模式">
          <button
            :aria-pressed="mode === 'balanced'"
            :class="{ active: mode === 'balanced' }"
            @click="filter('mode', '')"
          >
            为你精选</button
          ><button
            :aria-pressed="mode === 'explore'"
            :class="{ active: mode === 'explore' }"
            @click="filter('mode', 'explore')"
          >
            <Icon name="Sparkles" :size="14" />探索发现
          </button>
        </div>
      </div>
      <p class="control-description">
        {{
          mode === "explore"
            ? "为相对小众的好电影多留几个位置，试试平时不会点开的故事。"
            : "结合你的口味与电影口碑，优先推荐更合拍的故事。"
        }}
      </p>
      <div class="mood-genre-chips" role="group" aria-label="本次想看的类型">
        <button
          :aria-pressed="!selectedGenres.length"
          :class="{ active: !selectedGenres.length }"
          @click="filter('genres', '')"
        >
          不限类型</button
        ><button
          v-for="genre in expanded ? MOVIE_GENRES : MOVIE_GENRES.slice(0, 8)"
          :key="genre"
          :aria-pressed="selectedGenres.includes(genre)"
          :class="{ active: selectedGenres.includes(genre) }"
          :disabled="
            !selectedGenres.includes(genre) && selectedGenres.length >= 3
          "
          @click="toggleGenre(genre)"
        >
          {{ genre }}</button
        ><button
          class="more-genres"
          :aria-expanded="expanded"
          @click="expanded = !expanded"
        >
          {{ expanded ? "收起" : "更多类型" }}
        </button>
      </div>
      <div v-if="!expanded && extraGenres.length" class="selected-extra">
        <button
          v-for="genre in extraGenres"
          :key="genre"
          @click="toggleGenre(genre)"
        >
          {{ genre }} ×
        </button>
      </div>
      <div class="recommendation-control-bottom">
        <span>最多选 3 种 · 只影响本次推荐</span
        ><label
          >留给电影的时间<select
            :value="maxRuntime"
            aria-label="电影时长"
            @change="
              filter('maxRuntime', ($event.target as HTMLSelectElement).value)
            "
          >
            <option value="0">时间不限</option>
            <option value="90">90 分钟以内</option>
            <option value="120">2 小时以内</option>
            <option value="180">3 小时以内</option>
          </select></label
        >
      </div>
    </section>
    <div class="recommendation-context">
      <p v-if="user && preferenceCount">
        从你的
        {{ preferenceCount }}
        部观影选择中寻找共鸣，已收藏、评分和隐藏的电影不会重复出现。
      </p>
      <p v-else-if="user">
        评分和收藏会让推荐逐渐了解你，也可以先设置长期偏好。
      </p>
      <p v-else>现在就能按口味找电影。登录后，评分与片单会帮助我们更了解你。</p>
      <RouterLink v-if="user" to="/profile" class="text-link"
        >调整长期偏好<Icon name="ArrowUpRight" :size="15" /></RouterLink
      ><button v-else class="text-link" @click="authOpen = true">
        登录记录口味<Icon name="ArrowUpRight" :size="15" />
      </button>
    </div>
    <div class="section-heading recommendation-results-heading">
      <div>
        <p class="eyebrow">
          {{
            mode === "explore"
              ? "A LITTLE OFF THE BEATEN PATH"
              : "SELECTED FOR THIS MOMENT"
          }}
        </p>
        <h2>
          {{
            mode === "explore" ? "在熟悉之外，遇见喜欢。" : "为此刻的你，挑选。"
          }}
        </h2>
      </div>
      <span class="muted small"
        >{{ picks.length }} 部电影<span v-if="batches.length">
          · 第 {{ batch + 1 }} 组</span
        ></span
      >
    </div>
    <div v-if="error" class="recommendation-error" role="alert">
      <p>{{ error }}</p>
      <button class="text-link" @click="load(!picks.length)">重试</button>
    </div>
    <div
      v-if="(!ready || loading) && !picks.length"
      class="movie-grid"
      aria-busy="true"
    >
      <div v-for="i in 6" :key="i" class="skeleton-card">
        <div />
        <span />
      </div>
    </div>
    <div v-else-if="!picks.length && !error" class="empty-state">
      <Icon name="Search" :size="38" />
      <h2>这次的条件，还没遇到合适的电影。</h2>
      <p>放宽类型或时长，再找找。已看过的电影仍保留在片单里。</p>
      <button
        v-if="selectedGenres.length || maxRuntime"
        class="button primary"
        @click="clearFilters"
      >
        放宽筛选条件</button
      ><RouterLink v-else to="/library" class="button secondary"
        >打开我的片单</RouterLink
      >
    </div>
    <div v-else-if="picks.length" class="movie-grid" :aria-busy="loading">
      <div v-for="(pick, i) in picks" :key="pick.movie.id">
        <MovieCard
          :movie="pick.movie"
          :reason="pick.reason"
          :index="i"
          :request-id="pick.requestId"
        />
        <div class="pick-footnote">
          <span v-if="pick.exploration" class="exploration-badge"
            ><Icon name="Sparkles" :size="11" />探索发现</span
          ><button
            v-if="user"
            class="dismiss-pick"
            :disabled="dismissing !== null || loading"
            :aria-label="`不再推荐《${pick.movie.title}》`"
            @click="dismiss(pick.movie.id)"
          >
            {{ dismissing === pick.movie.id ? "正在调整…" : "不再推荐" }}
          </button>
        </div>
      </div>
    </div>
    <div v-if="batches.length" class="recommendation-pagination">
      <p role="status">
        {{
          loading
            ? "正在寻找下一组电影…"
            : exhausted
              ? "符合条件的推荐已经看完，可以放宽筛选或重新开始。"
              : seen.length >= 120
                ? "已发现 120 部电影，调整条件或重新开始看看。"
                : "下一组会避开本次已经展示过的电影。"
        }}
      </p>
      <div>
        <button
          class="button secondary"
          :disabled="loading || batch === 0"
          @click="batch--"
        >
          <Icon name="ChevronLeft" :size="16" />上一组</button
        ><button
          class="button primary"
          :disabled="!canNext || dismissing !== null"
          @click="next"
        >
          {{ loading ? "挑选中…" : "换一组"
          }}<Icon name="ArrowRight" :size="16" /></button
        ><button
          v-if="exhausted || seen.length >= 120"
          class="text-link"
          :disabled="loading"
          @click="load(true)"
        >
          重新开始
        </button>
      </div>
    </div>
  </div>
</template>

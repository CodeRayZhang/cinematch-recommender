<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { api } from "../api";
import { MOVIE_GENRES } from "../genres";
import {
  user,
  ready,
  authOpen,
  libraryRevision,
  loadSession,
  sessionError,
  notify,
} from "../state";
import type { Movie, Profile, LibraryPage, LibrarySummary } from "../types";
import Icon from "../components/Icon.vue";
import ReviewEditor from "../components/ReviewEditor.vue";
import ObservationSettings from "../components/ObservationSettings.vue";
const genres = MOVIE_GENRES;
const form = reactive({
  displayName: "",
  bio: "",
  preferredGenres: [] as string[],
});
const profile = ref<Profile>(),
  hidden = ref<Movie[]>([]),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  editing = ref<Movie | null>(null),
  showHidden = ref(false);
const summary = ref<LibrarySummary>({
  watching: 0,
  watched: 0,
  rated: 0,
  minutes: 0,
  average: null,
  genres: [],
});
const reviewPage = ref(1),
  archive = ref<LibraryPage>({ items: [], total: 0, page: 1, size: 6 });
const minutes = computed(() => summary.value.minutes);
const average = computed(() =>
  summary.value.average === null ? "—" : summary.value.average.toFixed(1),
);
const topGenres = computed(() =>
  summary.value.genres.map((g) => [g.name, g.count] as const),
);
const movieMap = computed(
  () => new Map(archive.value.items.map((row) => [row.movie.id, row.movie])),
);
const archiveReviews = computed(() =>
  archive.value.items.map((row) => ({
    movieId: row.movie.id,
    stars: row.stars || 0,
    body: row.body || "",
  })),
);
let archiveRequest = 0;
async function loadArchive() {
  const id = ++archiveRequest;
  if (!user.value) {
    archive.value = { items: [], total: 0, page: 1, size: 6 };
    summary.value = {
      watching: 0,
      watched: 0,
      rated: 0,
      minutes: 0,
      average: null,
      genres: [],
    };
    return;
  }
  try {
    const [s, a] = await Promise.all([
      api<LibrarySummary>("/me/library/summary"),
      api<LibraryPage>(
        `/me/library/page?tab=RATED&page=${reviewPage.value}&size=6`,
      ),
    ]);
    if (id === archiveRequest) {
      summary.value = s;
      archive.value = a;
      const max = Math.max(1, Math.ceil(a.total / 6));
      if (reviewPage.value > max) reviewPage.value = max;
    }
  } catch (e) {
    if (id === archiveRequest && (e as Error).name !== "AbortError")
      error.value = (e as Error).message;
  }
}
watch([libraryRevision, reviewPage], () => void loadArchive(), {
  immediate: true,
});
watch(
  () => user.value?.id,
  () => {
    reviewPage.value = 1;
    archiveRequest++;
    archive.value = { items: [], total: 0, page: 1, size: 6 };
    summary.value = {
      watching: 0,
      watched: 0,
      rated: 0,
      minutes: 0,
      average: null,
      genres: [],
    };
  },
);
let request = 0;
async function load() {
  const id = ++request;
  if (!user.value) {
    profile.value = undefined;
    hidden.value = [];
    loading.value = false;
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const [p, h] = await Promise.all([
      api<Profile>("/me/profile"),
      api<Movie[]>("/me/feedback"),
    ]);
    if (id !== request) return;
    profile.value = p;
    hidden.value = h;
    Object.assign(form, {
      displayName: p.displayName,
      bio: p.bio,
      preferredGenres: p.preferredGenres ? p.preferredGenres.split(",") : [],
    });
  } catch (e) {
    if (id === request) error.value = (e as Error).message;
  } finally {
    if (id === request) loading.value = false;
  }
}
watch(() => user.value?.id, load, { immediate: true });
function toggle(genre: string) {
  const i = form.preferredGenres.indexOf(genre);
  if (i >= 0) form.preferredGenres.splice(i, 1);
  else if (form.preferredGenres.length < 8) form.preferredGenres.push(genre);
}
async function save() {
  busy.value = true;
  error.value = "";
  try {
    profile.value = await api<Profile>("/me/profile", {
      method: "PUT",
      body: JSON.stringify(form),
    });
    await loadSession();
    notify("个人资料和观影偏好已保存");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function restore(id: number) {
  busy.value = true;
  error.value = "";
  try {
    await api(`/me/feedback/${id}`, { method: "DELETE" });
    hidden.value = hidden.value.filter((m) => m.id !== id);
    notify("已恢复推荐");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="page-container inner-page profile-page">
    <div class="personal-intro">
      <div>
        <p class="eyebrow">A PORTRAIT IN PICTURES</p>
        <h1>
          你好，<span class="serif-accent"
            >{{ user?.displayName || "影迷" }}。</span
          >
        </h1>
        <p>你喜欢的电影，慢慢拼成你的样子。</p>
      </div>
      <RouterLink v-if="user" to="/library" class="button secondary"
        >整理片单<Icon name="ArrowUpRight" :size="16"
      /></RouterLink>
    </div>
    <div v-if="!ready || loading" class="empty-state" aria-busy="true">
      正在打开你的电影档案…
    </div>
    <div v-else-if="sessionError" class="empty-state" role="alert">
      <p>{{ sessionError }}</p>
      <button class="button secondary" @click="loadSession">重试</button>
    </div>
    <div v-else-if="!user" class="empty-state">
      <Icon name="Film" :size="44" />
      <h2>每个影迷，都有自己的故事。</h2>
      <p>登录后，设置偏好、管理资料和观影记录。</p>
      <button class="button primary" @click="authOpen = true">
        登录 / 注册
      </button>
    </div>
    <template v-else>
      <p v-if="error" class="form-error" role="alert">
        {{ error }}
        <button v-if="!profile" class="text-link" @click="load">重试</button>
      </p>
      <div class="profile-stats">
        <div>
          <span>看过的电影</span
          ><strong>{{ summary.watched }}<small>部</small></strong>
        </div>
        <div>
          <span>等待观看</span
          ><strong>{{ summary.watching }}<small>部</small></strong>
        </div>
        <div>
          <span>记录中的观影时长</span
          ><strong>{{ (minutes / 60).toFixed(1) }}<small>小时</small></strong>
        </div>
        <div>
          <span>我的平均评分</span
          ><strong>{{ average }}<small>/ 5</small></strong>
        </div>
      </div>
      <div v-if="profile" class="profile-layout">
        <form class="profile-panel profile-form" @submit.prevent="save">
          <p class="eyebrow">THE PERSON BEHIND THE TICKET</p>
          <h2>我的资料</h2>
          <p class="muted small">
            @{{ user.username }} · 加入于 {{ profile.joinedAt.slice(0, 10) }}
          </p>
          <label
            >昵称<input
              v-model="form.displayName"
              maxlength="40"
              required
              :disabled="busy"
              autocomplete="nickname"
          /></label>
          <label
            >个人简介<textarea
              v-model="form.bio"
              rows="3"
              maxlength="280"
              :disabled="busy"
              placeholder="用一句话介绍你的电影口味"
            /></label
          ><span class="muted small">{{ form.bio.length }} / 280</span>
          <fieldset :disabled="busy">
            <legend>
              我偏爱的类型
              <span class="muted small"
                >{{ form.preferredGenres.length }} / 8</span
              >
            </legend>
            <p class="muted small">
              帮助我们准备第一份推荐，以后的评分也会参与调整。
            </p>
            <div class="preference-chips">
              <button
                v-for="genre in genres"
                :key="genre"
                type="button"
                :aria-pressed="form.preferredGenres.includes(genre)"
                :class="{ active: form.preferredGenres.includes(genre) }"
                :disabled="
                  !form.preferredGenres.includes(genre) &&
                  form.preferredGenres.length >= 8
                "
                @click="toggle(genre)"
              >
                {{ genre }}
              </button>
            </div>
          </fieldset>
          <button
            class="button primary"
            :disabled="busy || !form.displayName.trim()"
          >
            {{ busy ? "保存中…" : "保存个人资料"
            }}<Icon name="Check" :size="16" />
          </button>
        </form>
        <div class="profile-side">
          <section class="profile-panel taste-panel">
            <p class="eyebrow">YOUR VIEWING DNA</p>
            <h2>最近的你，偏爱这些故事</h2>
            <p class="muted small">
              按看过的电影统计，一部电影可以属于多种类型。
            </p>
            <div v-if="topGenres.length" class="taste-bars">
              <div v-for="[genre, count] in topGenres" :key="genre">
                <div>
                  <span>{{ genre }}</span
                  ><small>{{ count }} 部</small>
                </div>
                <progress
                  :value="count"
                  :max="summary.watched"
                  :aria-label="`${genre}电影占比`"
                />
              </div>
            </div>
            <p v-else class="panel-empty">
              标记几部看过的电影，你的观影画像就会从这里生长。
            </p>
            <RouterLink to="/picks" class="text-link"
              >看看为我推荐的电影<Icon name="ArrowRight" :size="16"
            /></RouterLink>
          </section>
          <section class="profile-panel">
            <p class="eyebrow">YOU SET THE DIRECTION</p>
            <h2>
              不再推荐 <span class="muted small">{{ hidden.length }} 部</span>
            </h2>
            <p class="muted small">
              随时恢复。这个选择只影响推荐，不会删除电影或评分。
            </p>
            <p v-if="!hidden.length" class="panel-empty">
              推荐页中可以告诉我们哪些电影不想再看到。
            </p>
            <div
              v-for="movie in hidden.slice(0, showHidden ? hidden.length : 5)"
              :key="movie.id"
              class="hidden-movie"
            >
              <RouterLink :to="`/movie/${movie.id}`">{{
                movie.title
              }}</RouterLink
              ><button
                class="text-link"
                :disabled="busy"
                :aria-label="`恢复推荐《${movie.title}》`"
                @click="restore(movie.id)"
              >
                恢复
              </button>
            </div>
            <button
              v-if="hidden.length > 5"
              class="text-link"
              @click="showHidden = !showHidden"
            >
              {{ showHidden ? "收起" : "查看全部" }}
            </button>
          </section>
        </div>
      </div>
      <ObservationSettings />
      <section class="profile-reviews">
        <div class="section-heading">
          <div>
            <p class="eyebrow">THOUGHTS AFTER THE CREDITS</p>
            <h2>
              我的评分与短评
              <span class="muted small">{{ summary.rated }} 条</span>
            </h2>
          </div>
          <RouterLink to="/library" class="text-link"
            >打开片单<Icon name="ArrowUpRight" :size="16"
          /></RouterLink>
        </div>
        <p v-if="!summary.rated" class="panel-empty">
          电影散场之后，你的感受值得留下。
        </p>
        <div class="review-notes">
          <article v-for="review in archiveReviews" :key="review.movieId">
            <div>
              <RouterLink :to="`/movie/${review.movieId}`"
                ><h3>
                  {{ movieMap.get(review.movieId)?.title || "电影" }}
                </h3></RouterLink
              ><span class="accent">{{ "★".repeat(review.stars) }}</span>
            </div>
            <p>{{ review.body || "这一次，用评分记录心情。" }}</p>
            <button
              class="text-link"
              @click="editing = movieMap.get(review.movieId) || null"
            >
              编辑评分与短评<Icon name="Pencil" :size="14" />
            </button>
          </article>
        </div>
        <div v-if="archive.total > 6" class="pagination">
          <span
            >第 {{ reviewPage }} / {{ Math.ceil(archive.total / 6) }} 页</span
          ><button
            class="button secondary compact"
            :disabled="reviewPage === 1"
            @click="reviewPage--"
          >
            上一页</button
          ><button
            class="button secondary compact"
            :disabled="reviewPage >= Math.ceil(archive.total / 6)"
            @click="reviewPage++"
          >
            下一页
          </button>
        </div>
      </section>
    </template>
    <ReviewEditor :movie="editing" @close="editing = null" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import {
  user,
  ready,
  authOpen,
  library,
  movieStatus,
  sessionError,
  loadSession,
  loadLibrary,
  notify,
} from "../state";
import { api } from "../api";
import type { Movie, Entry } from "../types";
import Icon from "../components/Icon.vue";
import ReviewEditor from "../components/ReviewEditor.vue";
const tab = ref("ALL"),
  search = ref(""),
  sort = ref("recent"),
  selected = ref<number[]>([]),
  busy = ref(false),
  error = ref(""),
  page = ref(1),
  editing = ref<Movie | null>(null);
const undo = ref<
  { movieId: number; status: Entry["status"] | undefined }[] | null
>(null);
const watching = computed(
  () => library.entries.filter((e) => e.status === "WATCHLIST").length,
);
const watched = computed(
  () => library.entries.filter((e) => e.status === "WATCHED").length,
);
const reviewMap = computed(
  () => new Map(library.reviews.map((r) => [r.movieId, r])),
);
const movies = computed(() => {
  const order = new Map(
    (tab.value === "RATED" ? library.reviews : library.entries).map((e, i) => [
      e.movieId,
      i,
    ]),
  );
  return library.movies
    .filter((m) => {
      const status = movieStatus(m.id);
      return (
        (tab.value === "RATED"
          ? reviewMap.value.has(m.id)
          : tab.value === "ALL"
            ? !!status
            : status === tab.value) &&
        `${m.title} ${m.originalTitle} ${m.director}`
          .toLowerCase()
          .includes(search.value.trim().toLowerCase())
      );
    })
    .sort((a, b) =>
      sort.value === "rating"
        ? (reviewMap.value.get(b.id)?.stars || 0) -
            (reviewMap.value.get(a.id)?.stars || 0) || a.id - b.id
        : sort.value === "title"
          ? a.title.localeCompare(b.title, "zh-CN")
          : (order.get(a.id) ?? 999999) - (order.get(b.id) ?? 999999) ||
            b.id - a.id,
    );
});
const pages = computed(() => Math.max(1, Math.ceil(movies.value.length / 24)));
const visible = computed(() =>
  movies.value.slice((page.value - 1) * 24, page.value * 24),
);
const allSelected = computed(
  () =>
    visible.value.length > 0 &&
    visible.value.every((m) => selected.value.includes(m.id)),
);
watch([tab, search, sort], () => {
  page.value = 1;
  selected.value = [];
});
watch(pages, (n) => {
  page.value = Math.min(page.value, n);
});
watch(user, () => {
  selected.value = [];
  undo.value = null;
  editing.value = null;
});
watch(
  () => library.movies,
  () => {
    selected.value = selected.value.filter((id) =>
      library.movies.some((m) => m.id === id),
    );
  },
);
function selectAll() {
  selected.value = allSelected.value
    ? selected.value.filter((id) => !visible.value.some((m) => m.id === id))
    : [
        ...new Set([...selected.value, ...visible.value.map((m) => m.id)]),
      ].slice(0, 100);
}
async function change(
  action: "WATCHLIST" | "WATCHED" | "REMOVE",
  ids: number[],
) {
  if (busy.value || !ids.length) return;
  busy.value = true;
  error.value = "";
  const previous = ids.map((movieId) => ({
    movieId,
    status: movieStatus(movieId),
  }));
  try {
    await api("/me/library/batch", {
      method: "PUT",
      body: JSON.stringify({ movieIds: ids, action }),
    });
    undo.value = previous;
    selected.value = [];
    await loadLibrary();
    notify(
      action === "REMOVE"
        ? "已移出片单，评分与短评仍保留"
        : `已标记 ${ids.length} 部${action === "WATCHED" ? "看过" : "想看"}`,
    );
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function restore() {
  if (!undo.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    for (const action of ["WATCHLIST", "WATCHED", "REMOVE"] as const) {
      const movieIds = undo.value
        .filter((e) => (e.status || "REMOVE") === action)
        .map((e) => e.movieId);
      if (movieIds.length)
        await api("/me/library/batch", {
          method: "PUT",
          body: JSON.stringify({ movieIds, action }),
        });
    }
    undo.value = null;
    await loadLibrary();
    notify("已撤销上一次片单操作");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="page-container inner-page shelf-page">
    <div class="personal-intro">
      <div>
        <p class="eyebrow">YOUR STORIES, YOUR SHELF</p>
        <h1>把喜欢，<span class="serif-accent">留在这里。</span></h1>
        <p>想看的远方，看过的故事。都由你整理。</p>
      </div>
      <RouterLink v-if="user" to="/profile" class="button secondary"
        >个人中心<Icon name="ArrowUpRight" :size="16"
      /></RouterLink>
    </div>
    <div v-if="!ready" class="empty-state" aria-busy="true">
      正在打开你的片单…
    </div>
    <div v-else-if="sessionError" class="empty-state" role="alert">
      <p>{{ sessionError }}</p>
      <button class="button secondary" @click="loadSession">重试</button>
    </div>
    <div v-else-if="!user" class="empty-state library-gate">
      <Icon name="Bookmark" :size="44" />
      <h2>你的私人影院，还差一张入场券。</h2>
      <p>登录后，开始整理自己的电影地图。</p>
      <button class="button primary" @click="authOpen = true">
        创建我的片单
      </button>
    </div>
    <template v-else>
      <div class="library-stats">
        <div>
          <span>等待开启的故事</span
          ><strong>{{ watching }}<small>部想看</small></strong>
        </div>
        <div>
          <span>留在记忆里的电影</span
          ><strong>{{ watched }}<small>部看过</small></strong>
        </div>
        <div>
          <span>属于你的观影感受</span
          ><strong>{{ library.reviews.length }}<small>次评分</small></strong>
        </div>
      </div>
      <div class="library-toolbar shelf-toolbar">
        <div class="genre-tabs">
          <button
            v-for="[value, label] in [
              ['ALL', '全部收藏'],
              ['WATCHLIST', '想看'],
              ['WATCHED', '看过'],
              ['RATED', '已评分'],
            ]"
            :key="value"
            :class="{ active: tab === value }"
            @click="tab = value!"
          >
            {{ label }}
          </button>
        </div>
        <div class="shelf-filters">
          <label class="search-box"
            ><Icon name="Search" :size="16" /><input
              v-model="search"
              aria-label="搜索我的片单"
              placeholder="片名或导演" /></label
          ><select v-model="sort" aria-label="片单排序">
            <option value="recent">最近更新</option>
            <option value="rating">我的评分</option>
            <option value="title">电影名称</option>
          </select>
        </div>
      </div>
      <p v-if="tab === 'RATED'" class="muted small">
        这里也保留已移出片单的评分记录。
      </p>
      <div v-if="undo" class="undo-note" role="status">
        <span>片单已更新。评分和短评独立保存。</span
        ><button class="text-link" :disabled="busy" @click="restore">
          撤销上一次操作</button
        ><button
          class="icon-button"
          aria-label="关闭撤销提示"
          @click="undo = null"
        >
          <Icon name="X" :size="14" />
        </button>
      </div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div v-if="movies.length" class="shelf-batch">
        <label
          ><input
            type="checkbox"
            :checked="allSelected"
            :disabled="busy"
            @change="selectAll"
          />选择本页</label
        ><span>{{
          selected.length
            ? `已选 ${selected.length} 部`
            : `共 ${movies.length} 部`
        }}</span>
        <div v-if="selected.length" class="batch-actions">
          <button :disabled="busy" @click="change('WATCHED', selected)">
            标记看过</button
          ><button :disabled="busy" @click="change('WATCHLIST', selected)">
            移到想看</button
          ><button :disabled="busy" @click="change('REMOVE', selected)">
            移出片单</button
          ><button :disabled="busy" @click="selected = []">取消选择</button>
        </div>
      </div>
      <div v-if="!movies.length" class="empty-state">
        <Icon name="Film" :size="36" />
        <h2>{{ search ? "没有找到这部电影" : "这里还在等待一个好故事。" }}</h2>
        <p>
          {{
            search
              ? "换个片名或导演试试看。"
              : "去发现电影，把想看的故事收进片单。"
          }}
        </p>
        <RouterLink to="/" class="button secondary"
          >去发现电影<Icon name="ArrowRight" :size="16"
        /></RouterLink>
      </div>
      <div v-else class="shelf-list" :aria-busy="busy">
        <article
          v-for="movie in visible"
          :key="movie.id"
          class="shelf-row"
          :class="{ selected: selected.includes(movie.id) }"
        >
          <input
            v-model="selected"
            type="checkbox"
            :value="movie.id"
            :disabled="
              busy || (!selected.includes(movie.id) && selected.length >= 100)
            "
            :aria-label="`选择《${movie.title}》`"
          />
          <RouterLink :to="`/movie/${movie.id}`" class="shelf-poster"
            ><img
              :src="movie.posterUrl"
              :alt="`${movie.title}海报`"
              loading="lazy"
          /></RouterLink>
          <div class="shelf-info">
            <RouterLink :to="`/movie/${movie.id}`"
              ><h3>{{ movie.title }}</h3></RouterLink
            >
            <p class="muted small">
              {{ movie.releaseYear }} ·
              {{ movie.genres.split(",").slice(0, 2).join(" / ") }} ·
              {{ movie.runtime }} 分钟
            </p>
            <p v-if="reviewMap.has(movie.id)" class="shelf-review">
              <span class="accent">{{
                "★".repeat(reviewMap.get(movie.id)!.stars)
              }}</span
              ><span>{{ reviewMap.get(movie.id)?.body || "已留下评分" }}</span>
            </p>
            <span v-else class="muted small">{{
              movieStatus(movie.id) === "WATCHED"
                ? "看过了，写点感受吧"
                : "留给下一个电影之夜"
            }}</span>
          </div>
          <div class="shelf-actions">
            <button
              class="button secondary compact"
              :disabled="busy"
              :aria-label="`${movieStatus(movie.id) === 'WATCHED' ? '移到想看' : '标记看过'}《${movie.title}》`"
              @click="
                change(
                  movieStatus(movie.id) === 'WATCHED' ? 'WATCHLIST' : 'WATCHED',
                  [movie.id],
                )
              "
            >
              <Icon
                :name="
                  movieStatus(movie.id) === 'WATCHED' ? 'Check' : 'Bookmark'
                "
                :size="14"
              />{{
                movieStatus(movie.id) === "WATCHED"
                  ? "看过 · 改为想看"
                  : "标记看过"
              }}</button
            ><button
              class="icon-button"
              :disabled="busy"
              :aria-label="`编辑《${movie.title}》评分`"
              @click="editing = movie"
            >
              <Icon name="Pencil" :size="17" /></button
            ><button
              v-if="movieStatus(movie.id)"
              class="icon-button"
              :disabled="busy"
              :aria-label="`移出《${movie.title}》`"
              @click="change('REMOVE', [movie.id])"
            >
              <Icon name="Trash2" :size="17" />
            </button>
          </div>
        </article>
      </div>
      <div v-if="pages > 1" class="pagination">
        <span>第 {{ page }} / {{ pages }} 页</span>
        <div>
          <button
            class="button secondary compact"
            :disabled="page <= 1"
            @click="page--"
          >
            上一页</button
          ><button
            class="button secondary compact"
            :disabled="page >= pages"
            @click="page++"
          >
            下一页
          </button>
        </div>
      </div>
    </template>
    <ReviewEditor :movie="editing" @close="editing = null" />
  </div>
</template>

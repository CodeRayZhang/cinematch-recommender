<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api } from "../api";
import { user, ready, authOpen, notify } from "../state";
import type { Collection, CollectionPage, Movie, MoviePage } from "../types";
import Icon from "../components/Icon.vue";
const route = useRoute(),
  router = useRouter();
const id = computed(() => String(route.params.id || ""));
const shelves = ref<Collection[]>([]),
  detail = ref<CollectionPage>(),
  page = ref(1),
  loading = ref(false),
  busy = ref(false),
  error = ref("");
const pages = computed(() =>
  Math.max(1, Math.ceil((detail.value?.total || 0) / 24)),
);
const editor = ref<HTMLDialogElement>(),
  searchDialog = ref<HTMLDialogElement>(),
  editMode = ref(false),
  formError = ref("");
const form = reactive({
  name: "",
  description: "",
  coverMovieId: null as number | null,
});
const query = ref(""),
  results = ref<Movie[]>([]),
  selected = ref<number[]>([]),
  searchBusy = ref(false),
  searchError = ref("");
const deleting = ref(false);
let generation = 0,
  searchGeneration = 0;
async function load() {
  const current = ++generation;
  if (!user.value) {
    shelves.value = [];
    detail.value = undefined;
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    if (id.value) {
      const result = await api<CollectionPage>(
        `/me/collections/${id.value}?page=${page.value}&size=24`,
      );
      if (current === generation) {
        detail.value = result;
        if (page.value > pages.value) page.value = pages.value;
      }
    } else {
      const result = await api<Collection[]>("/me/collections");
      if (current === generation) shelves.value = result;
    }
  } catch (e) {
    if (current === generation && (e as Error).name !== "AbortError")
      error.value = (e as Error).message;
  } finally {
    if (current === generation) loading.value = false;
  }
}
watch(
  [id, () => user.value?.id],
  () => {
    generation++;
    detail.value = undefined;
    shelves.value = [];
    page.value = 1;
    deleting.value = false;
    editor.value?.close();
    searchDialog.value?.close();
    void load();
  },
  { immediate: true },
);
watch(page, () => void load());
async function edit(existing = false) {
  editMode.value = existing;
  formError.value = "";
  Object.assign(
    form,
    existing && detail.value
      ? {
          name: detail.value.collection.name,
          description: detail.value.collection.description,
          coverMovieId: detail.value.collection.coverMovieId,
        }
      : { name: "", description: "", coverMovieId: null },
  );
  await nextTick();
  editor.value?.showModal();
}
async function save() {
  busy.value = true;
  formError.value = "";
  try {
    const saved = await api<Collection>(
      editMode.value ? `/me/collections/${id.value}` : "/me/collections",
      { method: editMode.value ? "PUT" : "POST", body: JSON.stringify(form) },
    );
    editor.value?.close();
    notify(editMode.value ? "片单资料已保存" : "新的片单已创建");
    if (!editMode.value) await router.push(`/collections/${saved.id}`);
    else await load();
  } catch (e) {
    formError.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function action(path: string, method: string, body?: unknown) {
  busy.value = true;
  error.value = "";
  try {
    await api(`/me/collections/${id.value}${path}`, {
      method,
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function cover(movie: Movie) {
  if (!detail.value) return;
  await action("", "PUT", {
    name: detail.value.collection.name,
    description: detail.value.collection.description,
    coverMovieId: movie.id,
  });
}
async function removeCollection() {
  busy.value = true;
  error.value = "";
  try {
    await api(`/me/collections/${id.value}`, { method: "DELETE" });
    notify("自定义片单已删除，想看和评分保留");
    await router.push("/collections");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function exportCollection() {
  busy.value = true;
  error.value = "";
  try {
    const data = await api(`/me/collections/${id.value}/export`);
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = `cinematch-collection-${id.value}.json`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notify("片单已导出为 JSON");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function findMovies() {
  const current = ++searchGeneration;
  searchBusy.value = true;
  searchError.value = "";
  try {
    const data = await api<MoviePage>(
      `/movies?${new URLSearchParams({ q: query.value, size: "12" })}`,
    );
    if (current === searchGeneration) results.value = data.items;
  } catch (e) {
    if (current === searchGeneration) searchError.value = (e as Error).message;
  } finally {
    if (current === searchGeneration) searchBusy.value = false;
  }
}
async function openSearch() {
  query.value = "";
  selected.value = [];
  results.value = [];
  await nextTick();
  searchDialog.value?.showModal();
  void findMovies();
}
async function addMovies() {
  busy.value = true;
  searchError.value = "";
  try {
    await api(`/me/collections/${id.value}/movies`, {
      method: "POST",
      body: JSON.stringify({ movieIds: selected.value }),
    });
    searchDialog.value?.close();
    await load();
    notify("电影已收进片单");
  } catch (e) {
    searchError.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="page-container inner-page collections-page">
    <div class="personal-intro">
      <div>
        <p class="eyebrow">CURATED BY YOU</p>
        <h1>{{ detail?.collection.name || "给喜欢，一个名字。" }}</h1>
        <p>
          {{
            detail?.collection.description ||
            "为周末、一个主题，或某种心情，整理属于你的电影。"
          }}
        </p>
      </div>
      <div class="collection-tools">
        <RouterLink
          :to="id ? '/collections' : '/library'"
          class="button secondary"
          >{{ id ? "全部自定义片单" : "想看与看过" }}</RouterLink
        ><button v-if="user && !id" class="button primary" @click="edit()">
          <Icon name="Plus" :size="16" />新建片单
        </button>
      </div>
    </div>
    <div v-if="!ready" class="empty-state">正在打开片单…</div>
    <div v-else-if="!user" class="empty-state">
      <Icon name="Bookmark" :size="40" />
      <h2>把喜欢的电影，编成自己的故事。</h2>
      <button class="button primary" @click="authOpen = true">
        登录并创建片单
      </button>
    </div>
    <template v-else>
      <p v-if="error" class="form-error" role="alert">
        {{ error }} <button class="text-link" @click="load">重试</button>
      </p>
      <div
        v-if="loading && !detail && !shelves.length"
        class="empty-state"
        aria-busy="true"
      >
        正在整理故事…
      </div>
      <template v-if="detail">
        <div class="collection-detail-bar">
          <span class="muted">{{ detail.total }} 部电影 · 私人片单</span>
          <div class="collection-tools">
            <button
              class="button primary compact"
              :disabled="busy"
              @click="openSearch"
            >
              添加电影</button
            ><button
              class="button secondary compact"
              :disabled="busy"
              @click="edit(true)"
            >
              编辑片单</button
            ><button
              class="button secondary compact"
              :disabled="busy"
              @click="exportCollection"
            >
              导出 JSON</button
            ><button
              class="text-link muted"
              :disabled="busy"
              @click="deleting = !deleting"
            >
              删除片单
            </button>
          </div>
        </div>
        <div v-if="deleting" class="undo-note">
          <span>删除这个自定义片单及备注？想看、看过和评分仍保留。</span
          ><button
            class="text-link danger-text"
            :disabled="busy"
            @click="removeCollection"
          >
            确认删除片单</button
          ><button class="text-link" @click="deleting = false">取消</button>
        </div>
        <div v-if="!detail.total && !loading" class="empty-state">
          <Icon name="Film" :size="42" />
          <h2>第一部电影，决定故事的开场。</h2>
          <p>从片库挑选电影，再为它们加上备注和顺序。</p>
          <button class="button primary" @click="openSearch">挑选电影</button>
        </div>
        <div class="collection-movies" :aria-busy="busy || loading">
          <article
            v-for="(item, index) in detail.items"
            :key="item.movie.id"
            class="collection-movie-row"
          >
            <RouterLink :to="`/movie/${item.movie.id}`"
              ><img
                :src="item.movie.posterUrl"
                :alt="`${item.movie.title}海报`"
                loading="lazy"
            /></RouterLink>
            <div class="collection-movie-info">
              <span class="eyebrow"
                >{{ String((page - 1) * 24 + index + 1).padStart(2, "0") }} /
                {{ item.movie.releaseYear }}</span
              ><RouterLink :to="`/movie/${item.movie.id}`"
                ><h2>{{ item.movie.title }}</h2></RouterLink
              >
              <p class="muted small">
                {{ item.movie.director }} · {{ item.movie.runtime }} 分钟
              </p>
              <label :for="`note-${item.movie.id}`" class="sr-only"
                >{{ item.movie.title }}的片单备注</label
              ><textarea
                :id="`note-${item.movie.id}`"
                v-model="item.note"
                maxlength="1000"
                rows="2"
                placeholder="为什么把这部电影放在这里？"
                :disabled="busy"
              /><button
                class="text-link"
                :disabled="busy"
                @click="
                  action(`/movies/${item.movie.id}`, 'PUT', { note: item.note })
                "
              >
                保存备注
              </button>
            </div>
            <div class="collection-row-actions">
              <button
                class="icon-button"
                :disabled="busy || (page - 1) * 24 + index === 0"
                :aria-label="`上移《${item.movie.title}》`"
                @click="
                  action(`/movies/${item.movie.id}/move`, 'POST', {
                    direction: 'up',
                  })
                "
              >
                <Icon name="ArrowUp" :size="16" /></button
              ><button
                class="icon-button"
                :disabled="busy || (page - 1) * 24 + index === detail.total - 1"
                :aria-label="`下移《${item.movie.title}》`"
                @click="
                  action(`/movies/${item.movie.id}/move`, 'POST', {
                    direction: 'down',
                  })
                "
              >
                <Icon name="ArrowDown" :size="16" /></button
              ><button
                class="text-link"
                :disabled="busy"
                @click="cover(item.movie)"
              >
                {{
                  detail.collection.coverMovieId === item.movie.id
                    ? "当前封面"
                    : "设为封面"
                }}</button
              ><button
                class="text-link muted"
                :disabled="busy"
                :aria-label="`从自定义片单移除《${item.movie.title}》`"
                @click="action(`/movies/${item.movie.id}`, 'DELETE')"
              >
                移出
              </button>
            </div>
          </article>
        </div>
        <div v-if="pages > 1" class="pagination">
          <span>第 {{ page }} / {{ pages }} 页</span
          ><button
            class="button secondary compact"
            :disabled="page === 1 || loading"
            @click="page--"
          >
            上一页</button
          ><button
            class="button secondary compact"
            :disabled="page === pages || loading"
            @click="page++"
          >
            下一页
          </button>
        </div>
      </template>
      <div v-else-if="!id && shelves.length" class="collection-grid">
        <RouterLink
          v-for="shelf in shelves"
          :key="shelf.id"
          :to="`/collections/${shelf.id}`"
          class="collection-card"
          ><img
            :src="shelf.coverUrl"
            :alt="`${shelf.name}封面`"
            loading="lazy" />
          <div>
            <span class="eyebrow">{{ shelf.movieCount }} FILMS · PRIVATE</span>
            <h2>{{ shelf.name }}</h2>
            <p>{{ shelf.description || "属于你的电影章节。" }}</p>
            <span class="text-link"
              >打开片单 <Icon name="ArrowUpRight" :size="16"
            /></span></div
        ></RouterLink>
      </div>
      <div v-else-if="!id && !loading && !error" class="empty-state">
        <Icon name="Bookmark" :size="44" />
        <h2>一个名字，一种电影心情。</h2>
        <p>试试“雨天重看”“科幻之夜”或“今年想看的十部”。</p>
        <button class="button primary" @click="edit()">创建第一张片单</button>
      </div>
    </template>
    <dialog
      ref="editor"
      class="auth-dialog collection-dialog"
      aria-labelledby="collection-editor-title"
    >
      <form class="dialog-inner" @submit.prevent="save">
        <p class="eyebrow">YOUR OWN CHAPTER</p>
        <h2 id="collection-editor-title">
          {{ editMode ? "编辑片单" : "新建片单" }}
        </h2>
        <label
          >片单名称<input
            v-model="form.name"
            required
            maxlength="80"
            :disabled="busy" /></label
        ><label
          >片单介绍<textarea
            v-model="form.description"
            maxlength="1000"
            rows="3"
            :disabled="busy"
            placeholder="用几句话，写下这个片单的主题。"
          />
        </label>
        <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
        <div class="editor-actions">
          <button class="button primary" :disabled="busy || !form.name.trim()">
            保存片单</button
          ><button
            type="button"
            class="button secondary"
            :disabled="busy"
            @click="editor?.close()"
          >
            取消
          </button>
        </div>
      </form>
    </dialog>
    <dialog
      ref="searchDialog"
      class="auth-dialog collection-search-dialog"
      aria-labelledby="collection-search-title"
    >
      <div class="dialog-inner">
        <h2 id="collection-search-title">为片单挑选电影</h2>
        <form class="collection-search" @submit.prevent="findMovies">
          <label class="search-box"
            ><Icon name="Search" :size="16" /><input
              v-model="query"
              aria-label="搜索要添加的电影"
              maxlength="100"
              placeholder="片名或导演" /></label
          ><button class="button secondary compact" :disabled="searchBusy">
            搜索
          </button>
        </form>
        <p v-if="searchError" class="form-error" role="alert">
          {{ searchError }}
        </p>
        <p v-if="searchBusy" class="muted">正在查找…</p>
        <p v-else-if="!results.length" class="muted">
          没有找到电影，换个关键词试试。
        </p>
        <div class="collection-search-results">
          <label v-for="movie in results" :key="movie.id"
            ><input
              v-model="selected"
              type="checkbox"
              :value="movie.id"
              :disabled="busy"
            /><img :src="movie.posterUrl" :alt="movie.title" /><span
              ><strong>{{ movie.title }}</strong
              ><small
                >{{ movie.releaseYear }} · {{ movie.director }}</small
              ></span
            ></label
          >
        </div>
        <div class="editor-actions">
          <button
            class="button primary"
            :disabled="busy || !selected.length"
            @click="addMovies"
          >
            加入 {{ selected.length }} 部电影</button
          ><button
            class="button secondary"
            :disabled="busy"
            @click="searchDialog?.close()"
          >
            关闭
          </button>
        </div>
      </div>
    </dialog>
  </div>
</template>

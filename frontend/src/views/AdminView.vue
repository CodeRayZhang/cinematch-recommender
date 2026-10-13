<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from "vue";
import { api } from "../api";
import { user, ready, authOpen, notify, loadLibrary } from "../state";
import type { Movie, MoviePage } from "../types";
import Icon from "../components/Icon.vue";
const stats = ref<Record<string, number>>({}),
  movies = ref<Movie[]>([]),
  users = ref<Record<string, unknown>[]>([]);
const q = ref(""),
  page = ref(1),
  total = ref(0),
  tab = ref("movies"),
  error = ref(""),
  busy = ref(false),
  formError = ref(""),
  saving = ref(false);
const dialog = ref<HTMLDialogElement>(),
  editingId = ref<number | null>(null),
  deleting = ref<number | null>(null);
const defaults = () => ({
  title: "",
  originalTitle: "",
  releaseYear: new Date().getFullYear(),
  genres: "剧情",
  runtime: 120,
  rating: 0,
  ratingCount: 0,
  director: "",
  country: "",
  synopsis: "",
  posterUrl: "/images/placeholder.svg",
  backdropUrl: "/images/placeholder.svg",
  featured: false,
});
const form = reactive(defaults());
const pages = computed(() => Math.ceil(total.value / 12));
let requestId = 0;
async function load() {
  if (user.value?.role !== "ADMIN") return;
  const id = ++requestId;
  busy.value = true;
  error.value = "";
  try {
    const [s, result, u] = await Promise.all([
      api<Record<string, number>>("/admin/stats"),
      api<MoviePage>(
        `/movies?${new URLSearchParams({ q: q.value, page: String(page.value), size: "12", sort: "recent" })}`,
      ),
      api<Record<string, unknown>[]>("/admin/users"),
    ]);
    if (id === requestId) {
      stats.value = s;
      movies.value = result.items;
      total.value = result.total;
      users.value = u;
    }
  } catch (e) {
    if (id === requestId) error.value = (e as Error).message;
  } finally {
    if (id === requestId) busy.value = false;
  }
}
watch([user, page], load, { immediate: true });
function search() {
  if (page.value === 1) load();
  else page.value = 1;
}
async function edit(movie?: Movie) {
  editingId.value = movie?.id || null;
  const { id: ignored, ...rest } = movie || { id: 0, ...defaults() };
  Object.assign(form, rest);
  formError.value = "";
  await nextTick();
  dialog.value?.showModal();
}
async function save() {
  saving.value = true;
  formError.value = "";
  try {
    await api(
      editingId.value ? `/admin/movies/${editingId.value}` : "/admin/movies",
      { method: editingId.value ? "PUT" : "POST", body: JSON.stringify(form) },
    );
    dialog.value?.close();
    notify("电影资料已保存");
    await load();
  } catch (e) {
    formError.value = (e as Error).message;
  } finally {
    saving.value = false;
  }
}
async function remove(movie: Movie) {
  deleting.value = movie.id;
  try {
    await api(`/admin/movies/${movie.id}`, { method: "DELETE" });
    notify("电影已删除");
    await loadLibrary();
    await load();
  } catch (e) {
    notify((e as Error).message);
  } finally {
    deleting.value = null;
  }
}
</script>
<template>
  <div class="page-container inner-page admin-page">
    <div class="section-heading">
      <div>
        <p class="eyebrow">BEHIND THE SCREEN</p>
        <h1>电影的幕后。</h1>
        <p class="muted">整理片库，照顾每一次相遇。</p>
      </div>
      <button
        v-if="user?.role === 'ADMIN'"
        class="button primary"
        @click="edit()"
      >
        <Icon name="Plus" :size="17" />添加电影
      </button>
    </div>
    <div v-if="!ready" class="empty-state">正在确认身份…</div>
    <div v-else-if="user?.role !== 'ADMIN'" class="empty-state">
      <Icon name="Settings" :size="40" />
      <h2>这里是策展人的工作间。</h2>
      <p>{{ user ? "当前账号没有管理权限。" : "请使用管理员账号登录。" }}</p>
      <button v-if="!user" class="button primary" @click="authOpen = true">
        登录管理账号</button
      ><RouterLink v-else to="/" class="button secondary"
        >回到发现电影</RouterLink
      >
    </div>
    <template v-else
      ><div class="admin-stats">
        <div
          v-for="(label, key) in {
            movies: '电影资料',
            users: '影迷账号',
            reviews: '观影评分',
            saved: '片单收藏',
          }"
          :key="key"
        >
          <span>{{ label }}</span
          ><strong>{{ stats[key] || 0 }}</strong>
        </div>
      </div>
      <div class="library-toolbar">
        <div class="genre-tabs">
          <button :class="{ active: tab === 'movies' }" @click="tab = 'movies'">
            电影管理</button
          ><button :class="{ active: tab === 'users' }" @click="tab = 'users'">
            用户概览
          </button>
        </div>
        <form
          v-if="tab === 'movies'"
          class="search-box"
          @submit.prevent="search"
        >
          <Icon name="Search" :size="16" /><input
            v-model="q"
            placeholder="搜索电影或导演"
            aria-label="管理片库搜索"
          /><button class="text-link" aria-label="执行管理搜索">搜索</button>
        </form>
      </div>
      <p v-if="error" class="form-error" role="alert">
        {{ error }}<button class="text-link" @click="load">重试</button>
      </p>
      <div v-if="tab === 'movies'" class="table-scroll" :aria-busy="busy">
        <table>
          <thead>
            <tr>
              <th>电影</th>
              <th>年份 / 类型</th>
              <th>参考评分</th>
              <th>甄选</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="movie in movies" :key="movie.id">
              <td>
                <div class="table-movie">
                  <img :src="movie.posterUrl" alt="" /><span
                    ><strong>{{ movie.title }}</strong
                    ><small>{{ movie.originalTitle }}</small></span
                  >
                </div>
              </td>
              <td>
                {{ movie.releaseYear
                }}<small>{{ movie.genres.split(",").join(" / ") }}</small>
              </td>
              <td>{{ movie.rating.toFixed(1) }}</td>
              <td>{{ movie.featured ? "本期甄选" : "—" }}</td>
              <td>
                <div class="table-actions">
                  <button
                    class="icon-button"
                    :aria-label="`编辑${movie.title}`"
                    @click="edit(movie)"
                  >
                    <Icon name="Pencil" :size="17" />
                  </button>
                  <details class="delete-confirm">
                    <summary
                      class="icon-button"
                      :aria-label="`删除${movie.title}`"
                    >
                      <Icon name="Trash2" :size="17" />
                    </summary>
                    <div>
                      <p>删除《{{ movie.title }}》及关联的片单、评分？</p>
                      <button
                        class="button danger"
                        :disabled="deleting === movie.id"
                        @click="remove(movie)"
                      >
                        确认删除
                      </button>
                    </div>
                  </details>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!movies.length && !busy" class="empty-state">
          没有找到对应电影。
        </div>
      </div>
      <div v-else class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>昵称</th>
              <th>账号</th>
              <th>身份</th>
              <th>加入时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="account in users" :key="String(account.id)">
              <td>{{ account.display_name }}</td>
              <td>{{ account.username }}</td>
              <td>{{ account.role === "ADMIN" ? "策展人" : "影迷" }}</td>
              <td>{{ String(account.created_at).slice(0, 10) }}</td>
            </tr>
          </tbody>
        </table>
        <p class="muted small">显示最近加入的 100 位用户。</p>
      </div>
      <div v-if="tab === 'movies' && pages > 1" class="pagination">
        <span>共 {{ total }} 部电影</span>
        <div>
          <button
            class="icon-button"
            :disabled="page <= 1"
            aria-label="管理上一页"
            @click="page--"
          >
            <Icon name="ChevronLeft" /></button
          ><span>{{ page }} / {{ pages }}</span
          ><button
            class="icon-button"
            :disabled="page >= pages"
            aria-label="管理下一页"
            @click="page++"
          >
            <Icon name="ChevronRight" />
          </button>
        </div>
      </div>
    </template>
    <dialog ref="dialog" class="edit-dialog">
      <div class="dialog-inner">
        <div class="section-heading">
          <h2>{{ editingId ? "编辑电影" : "添加一部好电影" }}</h2>
          <button
            class="icon-button"
            aria-label="关闭编辑窗口"
            @click="dialog?.close()"
          >
            <Icon name="X" />
          </button>
        </div>
        <form class="admin-form" @submit.prevent="save">
          <label
            >电影名称<input
              v-model="form.title"
              maxlength="160"
              required /></label
          ><label
            >原片名<input
              v-model="form.originalTitle"
              maxlength="200"
              required /></label
          ><label
            >上映年份<input
              v-model.number="form.releaseYear"
              type="number"
              min="1888"
              max="2100"
              required /></label
          ><label
            >时长（分钟）<input
              v-model.number="form.runtime"
              type="number"
              min="1"
              max="1440"
              required /></label
          ><label
            >导演<input
              v-model="form.director"
              maxlength="160"
              required /></label
          ><label
            >国家 / 地区<input
              v-model="form.country"
              maxlength="100"
              required /></label
          ><label class="span-two"
            >类型（英文逗号分隔）<input
              v-model="form.genres"
              maxlength="160"
              required /></label
          ><label
            >参考评分<input
              v-model.number="form.rating"
              type="number"
              min="0"
              max="10"
              step="0.1"
              required /></label
          ><label
            >参考评分人数<input
              v-model.number="form.ratingCount"
              type="number"
              min="0"
              required /></label
          ><label class="span-two"
            >海报地址<input
              v-model="form.posterUrl"
              maxlength="1000"
              required /></label
          ><label class="span-two"
            >背景图地址<input
              v-model="form.backdropUrl"
              maxlength="1000"
              required /></label
          ><label class="span-two"
            >剧情简介<textarea
              v-model="form.synopsis"
              rows="4"
              maxlength="6000"
              required
            ></textarea></label
          ><label class="checkbox-label span-two"
            ><input
              v-model="form.featured"
              type="checkbox"
            />加入首页甄选</label
          >
          <p v-if="formError" class="form-error span-two" role="alert">
            {{ formError }}
          </p>
          <button class="button primary span-two" :disabled="saving">
            {{ saving ? "保存中…" : "保存电影资料" }}
          </button>
        </form>
      </div>
    </dialog>
  </div>
</template>

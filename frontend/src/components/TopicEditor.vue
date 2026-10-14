<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { api } from "../api";
import { notify } from "../state";
import { MOVIE_GENRES } from "../genres";
import type { CatalogTopic } from "../types";
const topics = ref<CatalogTopic[]>([]),
  editing = ref<string | null>(null),
  showForm = ref(false),
  busy = ref(false),
  error = ref("");
const defaults = () => ({
  title: "",
  description: "",
  genre: "",
  yearFrom: 0,
  yearTo: 0,
  maxRuntime: 0,
  position: 0,
  enabled: true,
});
const form = reactive(defaults());
async function load() {
  try {
    topics.value = await api<CatalogTopic[]>("/admin/topics");
  } catch (e) {
    if ((e as Error).name !== "AbortError") error.value = (e as Error).message;
  }
}
onMounted(load);
function edit(topic?: CatalogTopic) {
  editing.value = topic?.id || null;
  Object.assign(
    form,
    topic
      ? Object.fromEntries(
          Object.keys(defaults()).map((key) => [
            key,
            topic[key as keyof CatalogTopic],
          ]),
        )
      : defaults(),
  );
  showForm.value = true;
  error.value = "";
}
async function save() {
  busy.value = true;
  error.value = "";
  try {
    await api(
      editing.value ? `/admin/topics/${editing.value}` : "/admin/topics",
      { method: editing.value ? "PUT" : "POST", body: JSON.stringify(form) },
    );
    showForm.value = false;
    await load();
    notify("首页专题已保存");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function remove(id: string) {
  busy.value = true;
  error.value = "";
  try {
    await api(`/admin/topics/${id}`, { method: "DELETE" });
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
const deleting = ref("");
</script>
<template>
  <section class="topics-editor">
    <div class="section-heading">
      <div>
        <p class="eyebrow">SET THE SCENE</p>
        <h2>首页专题</h2>
      </div>
      <button class="button primary compact" @click="edit()">新建专题</button>
    </div>
    <p class="muted">
      为一种心情设置名称与筛选条件。启用后显示在首页，按顺序值从小到大排列。
    </p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <form v-if="showForm" class="topic-form" @submit.prevent="save">
      <label
        >专题名称<input v-model="form.title" required maxlength="80" /></label
      ><label
        >一句话介绍<input v-model="form.description" maxlength="240"
      /></label>
      <div class="catalog-filter-fields">
        <label
          >电影类型<select v-model="form.genre" aria-label="电影类型">
            <option value="">全部类型</option>
            <option v-for="genre in MOVIE_GENRES" :key="genre">
              {{ genre }}
            </option>
          </select></label
        ><label
          >起始年份<input
            v-model.number="form.yearFrom"
            type="number"
            min="0"
            max="2100" /></label
        ><label
          >结束年份<input
            v-model.number="form.yearTo"
            type="number"
            min="0"
            max="2100" /></label
        ><label
          >片长<select v-model.number="form.maxRuntime">
            <option :value="0">不限</option>
            <option :value="90">90 分钟以内</option>
            <option :value="120">120 分钟以内</option>
            <option :value="180">180 分钟以内</option>
          </select></label
        ><label
          >展示顺序<input
            v-model.number="form.position"
            type="number"
            min="0"
            max="100"
        /></label>
      </div>
      <span class="muted small">年份填 0 表示不限。</span
      ><label class="observation-toggle"
        ><input v-model="form.enabled" type="checkbox" />在首页展示</label
      >
      <div class="editor-actions">
        <button class="button primary" :disabled="busy">保存专题</button
        ><button
          type="button"
          class="button secondary"
          @click="showForm = false"
        >
          取消
        </button>
      </div>
    </form>
    <div class="topic-admin-list">
      <article v-for="topic in topics" :key="topic.id">
        <div>
          <h3>{{ topic.title }}</h3>
          <p class="muted small">
            {{ topic.enabled ? "已启用" : "未启用" }} · 顺序
            {{ topic.position }} · {{ topic.genre || "全部类型" }}
          </p>
        </div>
        <div class="collection-tools">
          <button class="text-link" :disabled="busy" @click="edit(topic)">
            编辑</button
          ><template v-if="deleting === topic.id"
            ><button
              class="text-link danger-text"
              :disabled="busy"
              @click="remove(topic.id)"
            >
              确认删除</button
            ><button class="text-link" @click="deleting = ''">
              取消
            </button></template
          ><button
            v-else
            class="text-link muted"
            :disabled="busy"
            @click="deleting = topic.id"
          >
            删除
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

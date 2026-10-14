<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from "vue";
import { api } from "../api";
import { notify } from "../state";
type Dataset = {
  name: string;
  ratings: number;
  users: number;
  items: number;
  mappedItems: number;
  available: boolean;
  message: string;
};
type Version = {
  id: string;
  dataset: string;
  status: string;
  iteration: number;
  parameters: {
    rank: number;
    iterations: number;
    regularization: number;
    seed: number;
  };
  summary: Record<string, unknown>;
  error: string | null;
  createdAt: string;
  active: boolean;
};
const datasets = ref<Dataset[]>([]),
  models = ref<Version[]>([]),
  error = ref(""),
  busy = ref(false);
const form = reactive({
  dataset: "local",
  rank: 16,
  iterations: 15,
  regularization: 0.1,
  seed: 42,
});
const running = computed(() =>
  models.value.some((m) => ["RUNNING", "QUEUED"].includes(m.status)),
);
const selected = computed(() =>
  datasets.value.find((d) => d.name === form.dataset),
);
let timer: ReturnType<typeof setTimeout> | undefined,
  stopped = false,
  revision = 0;
async function load() {
  const id = ++revision;
  try {
    const [d, m] = await Promise.all([
      api<Dataset[]>("/admin/recommender/datasets"),
      api<Version[]>("/admin/recommender/models"),
    ]);
    if (stopped || id !== revision) return;
    datasets.value = d;
    models.value = m;
    error.value = "";
  } catch (e) {
    if (!stopped) error.value = (e as Error).message;
  }
}
async function poll() {
  await load();
  if (!stopped) timer = setTimeout(poll, running.value ? 2000 : 15000);
}
async function action(path: string, method = "POST", body?: unknown) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await api(`/admin/recommender/${path}`, {
      method,
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    await load();
    clearTimeout(timer);
    if (!stopped) timer = setTimeout(poll, running.value ? 1000 : 15000);
    notify(
      path === "models" ? "训练任务已创建，完成后可手动启用" : "模型设置已更新",
    );
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function download(id: string) {
  try {
    const data = await api(`/admin/recommender/models/${id}/export`);
    if (stopped) return;
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = `cinematch-model-${id}.json`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (e) {
    error.value = (e as Error).message;
  }
}
const labels: Record<string, string> = {
  QUEUED: "等待训练",
  RUNNING: "训练中",
  READY: "训练完成",
  FAILED: "训练失败",
};
onMounted(poll);
onUnmounted(() => {
  stopped = true;
  clearTimeout(timer);
});
</script>
<template>
  <section class="model-manager" aria-label="推荐模型管理">
    <div class="section-heading">
      <div>
        <p class="eyebrow">RECOMMENDATION STUDIO</p>
        <h2>让评分成为推荐的依据。</h2>
        <p class="muted">
          训练全局协同模型，评分后即时更新个人口味。启用已有版本即可回退。
        </p>
      </div>
      <button class="button secondary" :disabled="busy" @click="load">
        刷新状态
      </button>
    </div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <form
      class="model-training-form"
      @submit.prevent="action('models', 'POST', form)"
    >
      <label class="model-dataset"
        >训练数据<select v-model="form.dataset">
          <option
            v-for="d in datasets"
            :key="d.name"
            :value="d.name"
            :disabled="!d.available"
          >
            {{ d.name === "local" ? "本站观影评分" : d.name }} ·
            {{ d.ratings.toLocaleString() }} 条
          </option>
        </select></label
      >
      <label
        >向量维度<input
          v-model.number="form.rank"
          type="number"
          min="1"
          max="32"
          required
      /></label>
      <label
        >迭代次数<input
          v-model.number="form.iterations"
          type="number"
          min="1"
          max="30"
          required
      /></label>
      <label
        >正则系数<input
          v-model.number="form.regularization"
          type="number"
          min="0.01"
          max="10"
          step="0.01"
          required
      /></label>
      <label
        >随机种子<input
          v-model.number="form.seed"
          type="number"
          min="0"
          max="2147483647"
          required
      /></label>
      <button
        class="button primary"
        :disabled="
          busy || running || !selected?.available || models.length >= 10
        "
      >
        {{ running ? "训练进行中…" : "开始训练" }}
      </button>
    </form>
    <p v-if="selected" class="muted small">
      {{ selected.users.toLocaleString() }} 位用户 ·
      {{ selected.items.toLocaleString() }} 部训练电影 ·
      {{ selected.mappedItems }} 部本站映射。{{ selected.message }}
    </p>
    <p class="muted small">
      训练完成不等于效果提升；请先按项目评估文档验证，再启用。最多保留 10
      个版本，原始评分与训练快照仅存于服务器。
    </p>
    <div v-if="!models.length" class="empty-state">
      <h3>从一个可验证的模型开始。</h3>
      <p>
        本站评分达到训练门槛，或在服务器准备 MovieLens 数据后，即可创建版本。
      </p>
    </div>
    <div class="model-versions">
      <article
        v-for="model in models"
        :key="model.id"
        class="model-version"
        :class="{ active: model.active }"
      >
        <div class="model-version-heading">
          <div>
            <span class="model-state">{{
              model.active ? "当前启用" : labels[model.status]
            }}</span>
            <h3>
              {{ model.dataset }} <small>· {{ model.id.slice(0, 8) }}</small>
            </h3>
          </div>
          <time>{{ new Date(model.createdAt).toLocaleString() }}</time>
        </div>
        <p class="muted small">
          ALS · {{ model.parameters.rank }} 维 · {{ model.iteration }}/{{
            model.parameters.iterations
          }}
          轮 · 正则 {{ model.parameters.regularization }}
        </p>
        <progress
          v-if="['RUNNING', 'QUEUED'].includes(model.status)"
          :value="model.iteration"
          :max="model.parameters.iterations"
          aria-label="模型训练进度"
        />
        <p v-if="model.status === 'READY'">
          {{ Number(model.summary.ratings || 0).toLocaleString() }} 条评分 ·
          {{ model.summary.mappedItems }} 部有效映射 · 训练
          {{ (Number(model.summary.trainingMillis || 0) / 1000).toFixed(1) }} 秒
        </p>
        <p v-if="model.error" class="form-error">{{ model.error }}</p>
        <div class="editor-actions">
          <button
            v-if="model.status === 'READY' && !model.active"
            class="button primary"
            :disabled="busy"
            @click="action(`models/${model.id}/activate`)"
          >
            启用此版本
          </button>
          <button
            v-if="model.active"
            class="button secondary"
            :disabled="busy"
            @click="action('disable')"
          >
            停用模型
          </button>
          <button
            v-if="model.status === 'READY'"
            class="text-link"
            @click="download(model.id)"
          >
            导出模型
          </button>
          <details
            v-if="
              !model.active && !['QUEUED', 'RUNNING'].includes(model.status)
            "
          >
            <summary class="text-link">删除版本</summary>
            <p class="small">
              删除后无法从后台恢复该版本，服务器训练快照仍保留。
            </p>
            <button
              class="text-link danger-text"
              :disabled="busy"
              @click="action(`models/${model.id}`, 'DELETE')"
            >
              确认删除版本
            </button>
          </details>
        </div>
      </article>
    </div>
  </section>
</template>

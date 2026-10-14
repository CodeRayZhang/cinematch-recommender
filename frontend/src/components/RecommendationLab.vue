<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from "vue";
import { api } from "../api";
import { user, library, ready, authOpen } from "../state";
import type { Movie } from "../types";
import ReviewEditor from "./ReviewEditor.vue";
import Icon from "./Icon.vue";
type Status = {
  tasteGenres: { name: string; count: number }[];
  engine: string;
  modelVersion: string | null;
  mappedRatings: number;
  mappedMovies: number;
  minimumRatings: number;
  ready: boolean;
  message: string;
  model: { rank?: number; trainingItems?: number };
};
const status = ref<Status | null>(null),
  movies = ref<Movie[]>([]),
  expanded = ref(false),
  editing = ref<Movie | null>(null),
  error = ref("");
const axis = computed(() => {
  const counts = new Map(
    (status.value?.tasteGenres || []).map((g) => [g.name, g.count]),
  );
  const top = [...counts]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, 5);
  for (const genre of ["剧情", "科幻", "动画", "喜剧", "犯罪"])
    if (top.length < 5 && !top.some(([g]) => g === genre)) top.push([genre, 0]);
  const max = Math.max(1, ...top.map(([, n]) => n));
  return top.map(([name, count], i) => ({
    name,
    count,
    value: count / max,
    angle: -Math.PI / 2 + (i * Math.PI * 2) / 5,
  }));
});
function point(angle: number, radius: number) {
  return [160 + Math.cos(angle) * radius, 155 + Math.sin(angle) * radius];
}
function polygon(scale: number, data = false) {
  return axis.value
    .map((a) => point(a.angle, 104 * scale * (data ? a.value : 1)).join(","))
    .join(" ");
}
const records = computed(() => library.reviews.length);
let controller: AbortController | undefined;
async function load() {
  controller?.abort();
  const request = new AbortController();
  controller = request;
  status.value = null;
  movies.value = [];
  editing.value = null;
  error.value = "";
  if (!ready.value) return;
  try {
    const [s, m] = await Promise.all([
      api<Status>("/recommendations/status", { signal: request.signal }),
      user.value
        ? api<Movie[]>("/me/recommendations/starters", {
            signal: request.signal,
          })
        : Promise.resolve([]),
    ]);
    if (!request.signal.aborted) {
      status.value = s;
      movies.value = m;
    }
  } catch (e) {
    if (!request.signal.aborted) error.value = (e as Error).message;
  }
}
watch([ready, () => user.value?.id, () => library.reviews], load, {
  immediate: true,
});
onUnmounted(() => controller?.abort());
</script>
<template>
  <section class="taste-onboarding recommendation-lab" aria-label="推荐实验室">
    <div class="lab-topline">
      <span><i class="lab-status-dot" /> CINEMATCH / DISCOVERY ENGINE</span
      ><span class="lab-status">{{
        status?.ready ? "PERSONALIZED" : "EXPLORING"
      }}</span>
    </div>
    <div class="lab-body">
      <div class="lab-story">
        <p class="eyebrow">BEYOND THE OBVIOUS</p>
        <h1>好电影很多。<br /><span>这一部，为什么是你？</span></h1>
        <p class="lab-description">
          每一次评分，都是一条线索。将你的观影选择与电影之间的关联结合，让下一次心动有迹可循。
        </p>
        <div class="lab-capabilities">
          <span
            ><Icon name="Sparkles" :size="13" />{{
              status?.ready ? "ALS 协同模型" : "内容与相似用户"
            }}</span
          ><span>即时口味更新</span><span>多样性重排</span>
        </div>
        <div class="lab-action">
          <button
            v-if="user && movies.length"
            class="button primary"
            :aria-expanded="expanded"
            @click="expanded = !expanded"
          >
            {{
              expanded
                ? "收起选片"
                : status?.ready
                  ? "继续校准我的口味"
                  : "建立我的观影指纹"
            }}<Icon name="ArrowUpRight" :size="17" /></button
          ><button
            v-else-if="!user"
            class="button primary"
            @click="authOpen = true"
          >
            开启我的专属推荐<Icon name="ArrowUpRight" :size="17" /></button
          ><RouterLink v-else to="/profile" class="text-link"
            >查看我的观影记录</RouterLink
          ><span
            v-if="status?.modelVersion && !status.ready"
            class="muted small"
            >{{
              user
                ? `${status.mappedRatings} / ${status.minimumRatings} 部已评分`
                : "从 3 部看过的电影开始"
            }}</span
          >
        </div>
      </div>
      <div class="lab-fingerprint">
        <div class="lab-orbit" aria-hidden="true" />
        <svg
          viewBox="0 0 320 310"
          role="img"
          aria-label="已评分电影的类型分布，按数量相对最多的类型归一化"
        >
          <defs>
            <linearGradient id="taste-fill" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#f39b75" stop-opacity=".48" />
              <stop offset="100%" stop-color="#9c8af5" stop-opacity=".18" />
            </linearGradient>
          </defs>
          <polygon
            v-for="scale in [0.25, 0.5, 0.75, 1]"
            :key="scale"
            :points="polygon(scale)"
            class="lab-radar-grid"
          />
          <line
            v-for="a in axis"
            :key="a.name"
            x1="160"
            y1="155"
            :x2="point(a.angle, 104)[0]"
            :y2="point(a.angle, 104)[1]"
            class="lab-radar-grid"
          />
          <polygon
            v-if="records"
            :points="polygon(1, true)"
            class="lab-radar-data"
          />
          <g v-for="a in axis" :key="a.name">
            <circle
              v-if="a.count"
              :cx="point(a.angle, 104 * a.value)[0]"
              :cy="point(a.angle, 104 * a.value)[1]"
              r="4"
              class="lab-radar-dot"
            />
            <text
              :x="point(a.angle, 132)[0]"
              :y="point(a.angle, 132)[1]"
              dominant-baseline="middle"
              text-anchor="middle"
            >
              {{ a.name }}
              <tspan
                v-if="a.count"
                :x="point(a.angle, 132)[0]"
                dy="16"
                class="lab-radar-count"
              >
                {{ a.count }} 部
              </tspan>
            </text>
          </g>
          <g v-if="!records">
            <circle cx="160" cy="155" r="8" class="lab-radar-dot" />
            <text x="160" y="188" text-anchor="middle" class="lab-radar-empty">
              等待你的第一条线索
            </text>
          </g>
        </svg>
        <div class="lab-fingerprint-caption">
          <span>TASTE SIGNATURE</span
          ><strong>{{
            records
              ? `${records} 部已评分 · 类型分布`
              : "你的观影指纹，尚待点亮"
          }}</strong>
        </div>
      </div>
    </div>
    <div class="lab-pipeline" aria-label="推荐计算流程">
      <div>
        <span class="lab-step">01 / SIGNAL</span
        ><strong>{{ user ? `${records} 条评分线索` : "从你的选择出发" }}</strong
        ><small>评分、收藏与长期偏好</small>
      </div>
      <Icon name="ArrowRight" :size="16" />
      <div>
        <span class="lab-step">02 / CONNECT</span
        ><strong>{{
          status?.model.rank
            ? `${status.model.rank} 维协同模型`
            : "内容 + 邻域关联"
        }}</strong
        ><small>{{
          status?.model.trainingItems
            ? `学习了 ${status.model.trainingItems.toLocaleString()} 部电影的关联`
            : "寻找故事、类型与口味的交集"
        }}</small>
      </div>
      <Icon name="ArrowRight" :size="16" />
      <div>
        <span class="lab-step">03 / DISCOVER</span
        ><strong>{{ status?.ready ? "已为你激活" : "探索正在发生" }}</strong
        ><small>{{
          status?.modelVersion
            ? `模型覆盖本站 ${status.mappedMovies} 部电影`
            : "兼顾合拍与意外，避开已看电影"
        }}</small>
      </div>
    </div>
    <p v-if="error" class="form-error" role="alert">
      {{ error }} <button class="text-link" @click="load">重试</button>
    </p>
    <details class="lab-explainer">
      <summary>
        这些图形与推荐依据来自哪里？<Icon name="ChevronDown" :size="13" />
      </summary>
      <p>
        图形统计你的已评分电影类型，一部多类型电影会计入多项，并不代表预测准确率。{{
          status?.message
        }}。模型未覆盖的电影继续依据内容和相似用户推荐。下方每张推荐卡片展示本次主要推荐依据；探索模式会给符合条件的小众电影更多机会。
      </p>
    </details>
    <div v-if="expanded && user" class="lab-onboarding">
      <div class="taste-heading">
        <div>
          <h3>选几部看过的电影，告诉我们真实感受。</h3>
          <p class="muted small">
            喜欢与不喜欢同样重要。保存评分后会加入看过片单，推荐立即重新计算。
          </p>
        </div>
        <span
          v-if="status?.modelVersion"
          class="taste-progress"
          role="status"
          >{{
            status.ready
              ? "协同模型已参与推荐"
              : `已评分 ${status.mappedRatings} / ${status.minimumRatings} 部`
          }}</span
        >
      </div>
      <div class="taste-movies">
        <button
          v-for="movie in movies"
          :key="movie.id"
          class="taste-movie"
          :aria-label="`为《${movie.title}》评分`"
          @click="editing = movie"
        >
          <img :src="movie.posterUrl" alt="" loading="lazy" /><span
            ><strong>{{ movie.title }}</strong
            ><small
              >{{ movie.releaseYear }} · {{ movie.genres.split(",")[0] }}</small
            ></span
          ><span class="text-link">评分</span>
        </button>
      </div>
    </div>
    <ReviewEditor :movie="editing" @close="editing = null" />
  </section>
</template>

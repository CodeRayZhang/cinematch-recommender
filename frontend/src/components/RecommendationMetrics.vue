<script setup lang="ts">
import { ref, watch } from "vue";
import { api } from "../api";
import type { RecommendationMetrics } from "../types";
const days = ref(30),
  data = ref<RecommendationMetrics>(),
  error = ref(""),
  loading = ref(false);
let current = 0;
async function load() {
  const request = ++current;
  loading.value = true;
  error.value = "";
  try {
    const result = await api<RecommendationMetrics>(
      `/admin/recommendation-metrics?days=${days.value}`,
    );
    if (request === current) data.value = result;
  } catch (e) {
    if (request === current && (e as Error).name !== "AbortError")
      error.value = (e as Error).message;
  } finally {
    if (request === current) loading.value = false;
  }
}
watch(days, () => void load(), { immediate: true });
const percent = (value: number | null) =>
  value === null ? "—" : `${(value * 100).toFixed(1)}%`;
const segment = (value: string) =>
  ({ new: "无评分", sparse: "1–19 条评分", active: "20 条及以上评分" })[
    value
  ] || value;
</script>
<template>
  <section class="metrics-panel">
    <div class="section-heading">
      <div>
        <p class="eyebrow">LEARN FROM REAL CHOICES</p>
        <h2>推荐效果观察</h2>
      </div>
      <label
        >观察窗口<select v-model.number="days" aria-label="推荐观察天数">
          <option :value="7">最近 7 天</option>
          <option :value="30">最近 30 天</option>
          <option :value="90">最近 90 天</option>
        </select></label
      >
    </div>
    <p class="muted">
      按算法版本、推荐模式与请求时的评分历史分组。实际曝光要求卡片至少一半进入视口并停留
      0.5 秒；点击也记为看到。
    </p>
    <p v-if="error" class="form-error" role="alert">
      {{ error }} <button class="text-link" @click="load">重试</button>
    </p>
    <div v-if="loading && !data" class="empty-state">正在整理记录…</div>
    <div v-else-if="!data?.groups.length" class="empty-state">
      <h3>还没有可观察的推荐记录。</h3>
      <p>真实使用后会逐步积累数据，这里不会填入演示转化率。</p>
    </div>
    <div v-else class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>版本 / 模式</th>
            <th>评分历史</th>
            <th>用户 / 请求</th>
            <th>返回 / 曝光</th>
            <th>点击</th>
            <th>收藏</th>
            <th>评分 / 高分</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="group in data.groups"
            :key="`${group.version}-${group.mode}-${group.segment}`"
          >
            <td>
              {{ group.version
              }}<small>{{
                group.mode === "explore" ? "探索发现" : "默认精选"
              }}</small>
            </td>
            <td>{{ segment(group.segment) }}</td>
            <td>{{ group.users }} / {{ group.requests }}</td>
            <td>{{ group.served }} / {{ group.exposed }}</td>
            <td>
              {{ group.clicked }}<small>{{ percent(group.clickRate) }}</small>
            </td>
            <td>
              {{ group.saved }}<small>{{ percent(group.saveRate) }}</small>
            </td>
            <td>
              {{ group.rated }} / {{ group.highlyRated
              }}<small>{{ percent(group.ratingRate) }}</small>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p class="muted small metrics-note">
      比例以曝光条目为分母；同次推荐的同部电影去重。收藏与评分归到过去 7
      天内最近一次已看到的推荐，高分为 4–5
      星。数据仅覆盖开启观察的登录用户，最多保留每人最近 200 次请求及 90
      天记录；这是行为观察，不是随机 A/B 实验，也不等于推荐准确率。
    </p>
  </section>
</template>

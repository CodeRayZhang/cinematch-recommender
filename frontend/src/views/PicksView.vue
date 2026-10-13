<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { api } from "../api";
import { user, ready, library, authOpen } from "../state";
import type { Pick } from "../types";
import Icon from "../components/Icon.vue";
import MovieCard from "../components/MovieCard.vue";
const picks = ref<Pick[]>([]),
  error = ref(""),
  loading = ref(true);
const preferenceCount = computed(
  () =>
    new Set([
      ...library.entries.map((e) => e.movieId),
      ...library.reviews.map((r) => r.movieId),
    ]).size,
);
let requestId = 0;
async function load() {
  const id = ++requestId;
  loading.value = true;
  error.value = "";
  try {
    const result = await api<Pick[]>("/recommendations");
    if (id === requestId) picks.value = result;
  } catch (e) {
    if (id === requestId) error.value = (e as Error).message;
  } finally {
    if (id === requestId) loading.value = false;
  }
}
watch([ready, user, () => library.entries], load, { immediate: true });
</script>
<template>
  <div class="page-container inner-page">
    <section class="personal-intro">
      <div>
        <p class="eyebrow">
          <Icon name="Sparkles" :size="15" /> A CINEMA THAT KNOWS YOU
        </p>
        <h1>越看，<span class="serif-accent">越懂你。</span></h1>
        <p>从你喜欢的故事出发，找到下一次心动。</p>
      </div>
      <span class="orbital-art" aria-hidden="true"
        ><i></i><i></i><Icon name="Sparkles" :size="30"
      /></span>
    </section>
    <div class="preference-note">
      <Icon name="Bookmark" :size="22" />
      <div>
        <strong>{{
          user && preferenceCount
            ? `从你的 ${preferenceCount} 部观影选择中，寻找共鸣`
            : "你的第一份推荐，从好电影开始"
        }}</strong>
        <p>
          收藏和评分会调整类型偏好；加入片单或已经评分的电影将不再重复推荐。
        </p>
      </div>
      <button v-if="!user" class="text-link" @click="authOpen = true">
        登录开启<Icon name="ArrowUpRight" :size="17" /></button
      ><RouterLink v-else to="/library" class="text-link"
        >我的片单<Icon name="ArrowUpRight" :size="17"
      /></RouterLink>
    </div>
    <div class="section-heading">
      <div>
        <p class="eyebrow">SELECTED FOR YOU</p>
        <h2>
          {{
            user && preferenceCount
              ? "为此刻的你，挑选。"
              : "初次见面，这些值得一看。"
          }}
        </h2>
      </div>
      <span class="muted small">{{ picks.length }} 部电影 · 随偏好更新</span>
    </div>
    <div v-if="error" class="empty-state" role="alert">
      <p>{{ error }}</p>
      <button class="button secondary" @click="load">重新加载</button>
    </div>
    <div v-else-if="loading" class="movie-grid" aria-busy="true">
      <div v-for="i in 6" :key="i" class="skeleton-card">
        <div></div>
        <span></span>
      </div>
    </div>
    <div v-else-if="!picks.length" class="empty-state">
      <Icon name="CheckCircle2" :size="40" />
      <h2>这一批好电影，你都发现了。</h2>
      <p>回到片单，挑一部今晚重温。</p>
      <RouterLink to="/library" class="button primary">打开我的片单</RouterLink>
    </div>
    <div v-else class="movie-grid">
      <MovieCard
        v-for="(pick, i) in picks"
        :key="pick.movie.id"
        :movie="pick.movie"
        :reason="pick.reason"
        :index="i"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import {
  user,
  ready,
  authOpen,
  library,
  movieStatus,
  sessionError,
  loadSession,
} from "../state";
import Icon from "../components/Icon.vue";
import MovieCard from "../components/MovieCard.vue";
const tab = ref("ALL"),
  search = ref("");
const watching = computed(
  () => library.entries.filter((e) => e.status === "WATCHLIST").length,
);
const watched = computed(
  () => library.entries.filter((e) => e.status === "WATCHED").length,
);
const movies = computed(() =>
  library.movies.filter(
    (m) =>
      (tab.value === "ALL" || movieStatus(m.id) === tab.value) &&
      `${m.title}${m.originalTitle}`
        .toLowerCase()
        .includes(search.value.toLowerCase()),
  ),
);
</script>
<template>
  <div class="page-container inner-page">
    <div class="personal-intro">
      <div>
        <p class="eyebrow">YOUR STORIES, YOUR SHELF</p>
        <h1>把喜欢，<span class="serif-accent">留在这里。</span></h1>
        <p>那些想看的远方，和已经走过的故事。</p>
      </div>
      <Icon name="Bookmark" :size="60" class="library-header-icon" />
    </div>
    <div v-if="!ready" class="empty-state" aria-busy="true">
      正在打开你的片单…
    </div>
    <div v-else-if="sessionError" class="empty-state" role="alert">
      <p>{{ sessionError }}</p>
      <button class="button secondary" @click="loadSession">重试</button>
    </div>
    <div v-else-if="!user" class="empty-state library-gate">
      <div class="empty-illustration"><Icon name="Bookmark" :size="44" /></div>
      <h2>你的私人影院，还差一张入场券。</h2>
      <p>登录后，把每一次心动收藏成自己的电影地图。</p>
      <button class="button primary" @click="authOpen = true">
        创建我的片单<Icon name="ArrowUpRight" :size="18" />
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
      <div class="library-toolbar">
        <div class="genre-tabs">
          <button :class="{ active: tab === 'ALL' }" @click="tab = 'ALL'">
            全部收藏</button
          ><button
            :class="{ active: tab === 'WATCHLIST' }"
            @click="tab = 'WATCHLIST'"
          >
            想看 {{ watching }}</button
          ><button
            :class="{ active: tab === 'WATCHED' }"
            @click="tab = 'WATCHED'"
          >
            看过 {{ watched }}
          </button>
        </div>
        <label class="search-box"
          ><Icon name="Search" :size="17" /><input
            v-model="search"
            aria-label="搜索我的片单"
            placeholder="在片单中查找"
        /></label>
      </div>
      <div v-if="!movies.length" class="empty-state">
        <Icon name="Film" :size="36" />
        <h2>
          {{ search ? "片单里还没有找到它" : "空白的片单，等一个好故事。" }}
        </h2>
        <p>
          {{
            search
              ? "换个关键词，再试一次。"
              : "点击电影海报上的加号，收藏下一部想看的电影。"
          }}
        </p>
        <RouterLink to="/" class="button secondary"
          >去发现电影<Icon name="ArrowRight" :size="16"
        /></RouterLink>
      </div>
      <div v-else class="movie-grid">
        <div v-for="movie in movies" :key="movie.id">
          <MovieCard :movie="movie" />
          <p class="library-status">
            <Icon
              :name="movieStatus(movie.id) === 'WATCHED' ? 'Check' : 'Bookmark'"
              :size="12"
            />{{ movieStatus(movie.id) === "WATCHED" ? "已看过" : "想看"
            }}<span v-if="library.reviews.find((r) => r.movieId === movie.id)"
              >我的评分
              {{ library.reviews.find((r) => r.movieId === movie.id)?.stars }} /
              5</span
            >
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import type { Movie } from "../types";
import { movieStatus, toggleWatchlist, notify } from "../state";
import Icon from "./Icon.vue";
import { ratingLabel, sourceNote } from "../movieSource";
defineProps<{ movie: Movie; reason?: string; index?: number }>();
const saving = ref(false);
async function save(id: number) {
  saving.value = true;
  try {
    await toggleWatchlist(id);
  } catch (e) {
    notify((e as Error).message);
  } finally {
    saving.value = false;
  }
}
function fallback(e: Event) {
  const img = e.target as HTMLImageElement;
  img.onerror = null;
  img.src = "/images/placeholder.svg";
}
</script>
<template>
  <article
    class="movie-card"
    :style="{ '--card-delay': `${((index || 0) % 6) * 35}ms` }"
  >
    <div class="poster-wrap">
      <RouterLink
        :to="`/movie/${movie.id}`"
        :aria-label="`查看《${movie.title}》`"
        class="poster-link"
      >
        <img
          :src="movie.posterUrl"
          :alt="`${movie.title}海报`"
          loading="lazy"
          width="400"
          height="600"
          @error="fallback"
        />
        <span class="poster-shade"></span>
        <span class="poster-open"
          >走进这部电影 <Icon name="ArrowUpRight" :size="17"
        /></span>
      </RouterLink>
      <span
        class="rating-badge"
        :title="`${ratingLabel(movie)} · ${sourceNote(movie)}`"
        ><Icon name="Star" :size="12" />{{ movie.rating.toFixed(1) }}</span
      >
      <button
        class="save-movie icon-button"
        :class="{ saved: movieStatus(movie.id) }"
        :disabled="saving"
        :aria-label="
          movieStatus(movie.id)
            ? `从片单移除《${movie.title}》`
            : `想看《${movie.title}》`
        "
        :aria-pressed="!!movieStatus(movie.id)"
        @click="save(movie.id)"
      >
        <Icon
          :name="
            saving ? 'LoaderCircle' : movieStatus(movie.id) ? 'Check' : 'Plus'
          "
          :size="19"
          :class="{ spinning: saving }"
        />
      </button>
    </div>
    <RouterLink :to="`/movie/${movie.id}`" class="movie-title">{{
      movie.title
    }}</RouterLink>
    <p class="movie-meta">
      {{ movie.releaseYear }} <span>·</span>
      {{ movie.genres.split(",").slice(0, 2).join(" / ") }}
    </p>
    <p v-if="reason" class="recommendation-reason">
      <Icon name="Sparkles" :size="12" />{{ reason }}
    </p>
  </article>
</template>

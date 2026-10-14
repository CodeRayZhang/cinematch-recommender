<script setup lang="ts">
import { ref, watch, nextTick } from "vue";
import { api } from "../api";
import { library, loadLibrary, notify, user } from "../state";
import type { Movie } from "../types";
import Icon from "./Icon.vue";
const props = defineProps<{ movie: Movie | null }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>(),
  stars = ref(0),
  body = ref(""),
  busy = ref(false),
  error = ref(""),
  deleting = ref(false),
  loadFailed = ref(false);
watch(
  () => props.movie,
  async (movie) => {
    await nextTick();
    if (!movie) {
      dialog.value?.close();
      return;
    }
    error.value = "";
    loadFailed.value = false;
    let review = library.reviews.find((r) => r.movieId === movie.id);
    try {
      const full = await api<{ movieId: number; stars: number; body: string }>(
        `/me/reviews/${movie.id}`,
      );
      if (props.movie?.id !== movie.id || !user.value) return;
      review = full;
    } catch (e) {
      if ((e as Error).name === "AbortError") return;
      loadFailed.value = true;
      error.value = (e as Error).message;
    }
    stars.value = review?.stars || 0;
    body.value = review?.body || "";
    deleting.value = false;
    dialog.value?.showModal();
  },
  { immediate: true },
);
watch(user, (current, previous) => {
  if (current?.id !== previous?.id) emit("close");
});
async function save(remove = false) {
  if (!props.movie || busy.value || loadFailed.value) return;
  busy.value = true;
  error.value = "";
  try {
    await api(`/me/reviews/${props.movie.id}`, {
      method: remove ? "DELETE" : "PUT",
      ...(!remove
        ? { body: JSON.stringify({ stars: stars.value, body: body.value }) }
        : {}),
    });
    await loadLibrary();
    notify(remove ? "评分和短评已删除，看过状态保留" : "观影记录已保存");
    emit("close");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <dialog
    ref="dialog"
    class="auth-dialog review-dialog"
    aria-labelledby="review-title"
    @close="emit('close')"
    @cancel="busy && $event.preventDefault()"
  >
    <div class="dialog-inner">
      <button
        class="icon-button close-dialog"
        aria-label="关闭评分编辑"
        :disabled="busy"
        @click="emit('close')"
      >
        <Icon name="X" />
      </button>
      <p class="eyebrow">YOUR TAKE ON THE STORY</p>
      <h2 id="review-title">{{ movie?.title }}</h2>
      <p class="muted">保存评分会将电影标记为看过。</p>
      <form @submit.prevent="save()">
        <div class="star-picker" role="group" aria-label="编辑我的评分">
          <button
            v-for="n in 5"
            :key="n"
            type="button"
            :class="{ filled: n <= stars }"
            :aria-label="`${n} 星`"
            :aria-pressed="stars === n"
            :disabled="busy"
            @click="stars = n"
          >
            <Icon name="Star" :size="28" />
          </button>
        </div>
        <label
          >我的短评<textarea
            v-model="body"
            rows="4"
            maxlength="2000"
            placeholder="留住电影带来的感受，短评选填"
            :disabled="busy"
          />
        </label>
        <span class="muted small">{{ body.length }} / 2000</span>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <div class="editor-actions">
          <button
            class="button primary"
            :disabled="busy || !stars || loadFailed"
          >
            {{ busy ? "保存中…" : "保存评分" }}</button
          ><button
            type="button"
            class="button secondary"
            :disabled="busy"
            @click="emit('close')"
          >
            取消
          </button>
        </div>
      </form>
      <div
        v-if="library.reviews.some((r) => r.movieId === movie?.id)"
        class="review-delete"
      >
        <template v-if="deleting"
          ><p>删除这条评分和短评？电影仍保留在看过片单中。</p>
          <button
            class="text-link danger-text"
            :disabled="busy"
            @click="save(true)"
          >
            确认删除评分</button
          ><button class="text-link" :disabled="busy" @click="deleting = false">
            保留
          </button></template
        >
        <button
          v-else
          class="text-link muted"
          :disabled="busy"
          @click="deleting = true"
        >
          删除评分与短评
        </button>
      </div>
    </div>
  </dialog>
</template>

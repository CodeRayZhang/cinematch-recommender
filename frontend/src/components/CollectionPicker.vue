<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { api } from "../api";
import { user, notify } from "../state";
import type { Collection } from "../types";
const props = defineProps<{ movieIds: number[] }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>(),
  shelves = ref<Collection[]>([]),
  selected = ref(""),
  name = ref(""),
  error = ref(""),
  busy = ref(false);
let request = 0;
watch(
  () => props.movieIds,
  async (ids) => {
    const current = ++request;
    await nextTick();
    if (!ids.length) {
      dialog.value?.close();
      return;
    }
    shelves.value = [];
    selected.value = "";
    name.value = "";
    error.value = "";
    dialog.value?.showModal();
    try {
      const result = await api<Collection[]>("/me/collections");
      if (current === request) {
        shelves.value = result;
        selected.value = result[0]?.id || "";
      }
    } catch (e) {
      if (current === request) error.value = (e as Error).message;
    }
  },
);
watch(
  () => user.value?.id,
  () => {
    request++;
    emit("close");
  },
);
async function save() {
  busy.value = true;
  error.value = "";
  try {
    let id = selected.value;
    if (!id) {
      const created = await api<Collection>("/me/collections", {
        method: "POST",
        body: JSON.stringify({
          name: name.value,
          description: "",
          coverMovieId: null,
        }),
      });
      id = created.id;
    }
    await api(`/me/collections/${id}/movies`, {
      method: "POST",
      body: JSON.stringify({ movieIds: props.movieIds }),
    });
    notify("已收进自定义片单");
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
    class="auth-dialog collection-dialog"
    aria-labelledby="collection-picker-title"
    @close="emit('close')"
  >
    <form class="dialog-inner" @submit.prevent="save">
      <p class="eyebrow">A PLACE FOR EVERY STORY</p>
      <h2 id="collection-picker-title">收进自定义片单</h2>
      <p class="muted">
        已选择 {{ movieIds.length }} 部电影。重复电影会自动跳过。
      </p>
      <label
        >选择片单<select v-model="selected" :disabled="busy">
          <option v-for="shelf in shelves" :key="shelf.id" :value="shelf.id">
            {{ shelf.name }}
          </option>
          <option value="">创建新片单…</option>
        </select></label
      >
      <label v-if="!selected"
        >新片单名称<input
          v-model="name"
          maxlength="80"
          required
          :disabled="busy"
          placeholder="例如：周末的科幻之夜"
      /></label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="editor-actions">
        <button
          class="button primary"
          :disabled="busy || (!selected && !name.trim())"
        >
          {{ busy ? "保存中…" : "加入片单" }}</button
        ><button
          class="button secondary"
          type="button"
          :disabled="busy"
          @click="emit('close')"
        >
          取消
        </button>
      </div>
    </form>
  </dialog>
</template>

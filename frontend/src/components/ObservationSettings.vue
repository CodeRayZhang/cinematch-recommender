<script setup lang="ts">
import { ref, watch } from "vue";
import { api } from "../api";
import { user, notify } from "../state";
const enabled = ref(true),
  busy = ref(false),
  error = ref(""),
  confirming = ref(false);
watch(
  () => user.value?.id,
  async () => {
    confirming.value = false;
    error.value = "";
    if (!user.value) return;
    try {
      enabled.value = (
        await api<{ enabled: boolean }>("/me/recommendation-settings")
      ).enabled;
    } catch (e) {
      if ((e as Error).name !== "AbortError")
        error.value = (e as Error).message;
    }
  },
  { immediate: true },
);
async function save() {
  busy.value = true;
  error.value = "";
  try {
    enabled.value = (
      await api<{ enabled: boolean }>("/me/recommendation-settings", {
        method: "PUT",
        body: JSON.stringify({ enabled: !enabled.value }),
      })
    ).enabled;
    notify(
      enabled.value ? "已开启推荐效果观察" : "已停止记录新的推荐曝光与互动",
    );
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function clear() {
  busy.value = true;
  error.value = "";
  try {
    await api("/me/recommendation-history", { method: "DELETE" });
    confirming.value = false;
    notify("推荐观察记录已清除，评分和片单保留");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="profile-panel observation-settings">
    <p class="eyebrow">YOUR CHOICES, YOUR CONTROL</p>
    <h2>推荐效果观察</h2>
    <p class="muted small">
      登录后，记录实际看到的推荐及后续点击、收藏与评分，用于按用户历史分组评估。最多保留最近
      200 次推荐，最长 90 天，不记录站外行为。
    </p>
    <label class="observation-toggle"
      ><input
        type="checkbox"
        :checked="enabled"
        :disabled="busy"
        @change="save"
      />允许记录新的推荐曝光与互动</label
    >
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <div v-if="confirming" class="editor-actions">
      <button class="text-link danger-text" :disabled="busy" @click="clear">
        确认清除观察记录</button
      ><button class="text-link" :disabled="busy" @click="confirming = false">
        取消
      </button>
    </div>
    <button
      v-else
      class="text-link muted"
      :disabled="busy"
      @click="confirming = true"
    >
      清除我的推荐观察记录
    </button>
  </section>
</template>

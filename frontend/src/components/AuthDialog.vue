<script setup lang="ts">
import { ref, watch, nextTick } from "vue";
import { api } from "../api";
import { authOpen, loadSession, notify, sessionError } from "../state";
import Icon from "./Icon.vue";
const dialog = ref<HTMLDialogElement>();
const mode = ref<"login" | "register">("login");
const username = ref(""),
  password = ref(""),
  displayName = ref(""),
  error = ref(""),
  busy = ref(false);
watch(authOpen, async (open) => {
  await nextTick();
  if (open) {
    error.value = "";
    dialog.value?.showModal();
  } else dialog.value?.close();
});
watch(mode, () => {
  error.value = "";
});
async function submit() {
  busy.value = true;
  error.value = "";
  try {
    await loadSession();
    if (sessionError.value) throw new Error(sessionError.value);
    if (mode.value === "register")
      await api("/auth/register", {
        method: "POST",
        body: JSON.stringify({
          username: username.value.trim(),
          password: password.value,
          displayName: displayName.value.trim(),
        }),
      });
    await api("/auth/login", {
      method: "POST",
      body: new URLSearchParams({
        username: username.value.trim().toLowerCase(),
        password: password.value,
      }),
    });
    await loadSession();
    if (sessionError.value) throw new Error(sessionError.value);
    authOpen.value = false;
    password.value = "";
    notify("欢迎回来，下一部好电影正在等你");
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
    class="auth-dialog"
    @close="authOpen = false"
    @click="
      (e) => {
        if (e.target === dialog) authOpen = false;
      }
    "
  >
    <div class="dialog-inner">
      <button
        class="icon-button close-dialog"
        aria-label="关闭登录窗口"
        @click="authOpen = false"
      >
        <Icon name="X" />
      </button>
      <span class="brand-symbol large"><Icon name="Film" :size="28" /></span>
      <p class="eyebrow">YOUR PERSONAL CINEMA</p>
      <h2>
        {{ mode === "login" ? "好久不见，影迷。" : "找到你的下一部好电影。" }}
      </h2>
      <p class="muted">收藏想看的电影，留住看过的心情。</p>
      <div class="auth-tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">
          登录</button
        ><button
          :class="{ active: mode === 'register' }"
          @click="mode = 'register'"
        >
          创建账号
        </button>
      </div>
      <form @submit.prevent="submit">
        <label v-if="mode === 'register'"
          >怎么称呼你<input
            v-model="displayName"
            autocomplete="nickname"
            maxlength="40"
            required
            placeholder="你的影迷昵称"
        /></label>
        <label
          >账号<input
            v-model="username"
            autocomplete="username"
            pattern="[A-Za-z0-9_]{3,32}"
            maxlength="32"
            required
            placeholder="3–32 位字母、数字或下划线"
        /></label>
        <label
          >密码<input
            v-model="password"
            type="password"
            :autocomplete="
              mode === 'login' ? 'current-password' : 'new-password'
            "
            minlength="8"
            maxlength="72"
            required
            placeholder="至少 8 个字符"
        /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="button primary full-width" :disabled="busy">
          {{
            busy
              ? "请稍候…"
              : mode === "login"
                ? "进入我的影院"
                : "开始我的观影旅程"
          }}<Icon name="ArrowRight" :size="18" />
        </button>
      </form>
      <p class="auth-note">你的片单，只属于你。</p>
    </div>
  </dialog>
</template>

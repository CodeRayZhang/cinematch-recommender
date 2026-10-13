import { reactive, ref } from "vue";
import { api, setCsrf } from "./api";
import type { Library, User } from "./types";
export const user = ref<User | null>(null);
export const ready = ref(false);
export const sessionError = ref("");
export const authOpen = ref(false);
export const library = reactive<Library>({
  entries: [],
  movies: [],
  reviews: [],
});
export const toast = ref("");
let toastTimer: ReturnType<typeof setTimeout>;
export function notify(message: string) {
  toast.value = message;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.value = ""), 3500);
}
export function requireUser() {
  if (user.value) return true;
  authOpen.value = true;
  return false;
}
export async function loadLibrary() {
  if (!user.value) {
    Object.assign(library, { entries: [], movies: [], reviews: [] });
    return;
  }
  Object.assign(library, await api<Library>("/me/library"));
}
export async function loadSession() {
  sessionError.value = "";
  try {
    const session = await api<{ user: User | null; csrfToken: string }>(
      "/session",
    );
    user.value = session.user;
    setCsrf(session.csrfToken);
    await loadLibrary();
  } catch (e) {
    sessionError.value = (e as Error).message;
  } finally {
    ready.value = true;
  }
}
export function movieStatus(id: number) {
  return library.entries.find((x) => x.movieId === id)?.status;
}
export async function toggleWatchlist(id: number) {
  if (!requireUser()) return;
  if (movieStatus(id)) {
    await api(`/me/library/${id}`, { method: "DELETE" });
    notify("已从片单移除");
  } else {
    await api(`/me/library/${id}`, {
      method: "PUT",
      body: JSON.stringify({ status: "WATCHLIST" }),
    });
    notify("已加入想看片单");
  }
  await loadLibrary();
}
export async function logout() {
  try {
    await api("/auth/logout", { method: "POST" });
    await loadSession();
    notify("已退出登录");
  } catch (e) {
    notify((e as Error).message);
  }
}

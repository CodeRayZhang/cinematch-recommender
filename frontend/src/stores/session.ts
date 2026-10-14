import { ref } from "vue";
import {
  api,
  invalidateRequests,
  onAuthenticationFailure,
  setCsrf,
} from "../api";
import type { User } from "../types";
import { notify } from "./ui";
export const user = ref<User | null>(null);
export const ready = ref(false);
export const sessionError = ref("");
export const authOpen = ref(false);
let request = 0;
const resources: { clear: () => void; load: () => Promise<void> }[] = [];
export function registerSessionResource(
  clear: () => void,
  load: () => Promise<void>,
) {
  resources.push({ clear, load });
}
function clearIdentity() {
  invalidateRequests();
  setCsrf("");
  user.value = null;
  resources.forEach((resource) => resource.clear());
}
onAuthenticationFailure(() => {
  request++;
  clearIdentity();
  ready.value = true;
  sessionError.value = "";
  authOpen.value = true;
  notify("登录已过期，请重新登录后继续");
});
export function requireUser() {
  if (user.value) return true;
  authOpen.value = true;
  return false;
}
export async function loadSession() {
  const current = ++request;
  sessionError.value = "";
  try {
    const session = await api<{ user: User | null; csrfToken: string }>(
      "/session",
    );
    if (current !== request) return;
    if (user.value?.id !== session.user?.id) clearIdentity();
    user.value = session.user;
    setCsrf(session.csrfToken);
    await Promise.all(resources.map((resource) => resource.load()));
  } catch (error) {
    if (current !== request || (error as Error).name === "AbortError") return;
    clearIdentity();
    sessionError.value = (error as Error).message;
  } finally {
    if (current === request) ready.value = true;
  }
}
export async function logout() {
  try {
    await api("/auth/logout", { method: "POST" });
    request++;
    clearIdentity();
    await loadSession();
    notify("已退出登录");
  } catch (error) {
    if ((error as Error).name !== "AbortError")
      notify((error as Error).message);
  }
}

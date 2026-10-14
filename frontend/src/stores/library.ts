import { computed, reactive, ref } from "vue";
import { api } from "../api";
import type { Library } from "../types";
import { registerSessionResource, requireUser, user } from "./session";
import { notify } from "./ui";
export const library = reactive<Library>({
  entries: [],
  movies: [],
  reviews: [],
});
export const libraryRevision = ref(0);
let request = 0;
const statuses = computed(
  () => new Map(library.entries.map((entry) => [entry.movieId, entry.status])),
);
function clear() {
  request++;
  Object.assign(library, { entries: [], movies: [], reviews: [] });
  libraryRevision.value++;
}
export async function loadLibrary() {
  const owner = user.value?.id;
  if (!owner) {
    clear();
    return;
  }
  const current = ++request;
  const result = await api<Library>("/me/library/state");
  if (current === request && owner === user.value?.id) {
    Object.assign(library, result);
    libraryRevision.value++;
  }
}
registerSessionResource(clear, loadLibrary);
export function movieStatus(id: number) {
  return statuses.value.get(id);
}
export async function toggleWatchlist(id: number) {
  if (!requireUser()) return;
  const owner = user.value!.id;
  const remove = !!movieStatus(id);
  await api(`/me/library/${id}`, {
    method: remove ? "DELETE" : "PUT",
    ...(remove ? {} : { body: JSON.stringify({ status: "WATCHLIST" }) }),
  });
  if (owner !== user.value?.id) return;
  await loadLibrary();
  notify(remove ? "已从片单移除" : "已加入想看片单");
}

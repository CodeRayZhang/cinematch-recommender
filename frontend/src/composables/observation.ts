import { onBeforeUnmount, watch, type Ref } from "vue";
import { api } from "../api";
export function useRecommendationObservation(
  element: Ref<HTMLElement | undefined>,
  context: () => { requestId?: string | null; movieId: number },
) {
  let observer: IntersectionObserver | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let seen = false;
  let visible = false;
  const cancelTimer = () => {
    if (timer) clearTimeout(timer);
    timer = undefined;
  };
  async function event(kind: "exposure" | "click") {
    const value = context();
    if (!value.requestId) return;
    try {
      await api("/me/recommendation-events", {
        method: "POST",
        body: JSON.stringify({ ...value, event: kind }),
      });
    } catch {
      /* Observation must not prevent navigation or a library write. */
    }
  }
  const visibility = () => {
    cancelTimer();
    if (visible && !document.hidden && !seen)
      timer = setTimeout(() => {
        seen = true;
        void event("exposure");
      }, 500);
  };
  document.addEventListener("visibilitychange", visibility);
  watch(
    [element, () => context().requestId, () => context().movieId],
    () => {
      observer?.disconnect();
      cancelTimer();
      seen = false;
      visible = false;
      if (!element.value || !context().requestId) return;
      observer = new IntersectionObserver(
        (entries) => {
          visible = entries.some(
            (entry) => entry.isIntersecting && entry.intersectionRatio >= 0.5,
          );
          visibility();
        },
        { threshold: [0.5] },
      );
      observer.observe(element.value);
    },
    { flush: "post", immediate: true },
  );
  onBeforeUnmount(() => {
    observer?.disconnect();
    cancelTimer();
    document.removeEventListener("visibilitychange", visibility);
  });
  return { click: () => event("click") };
}

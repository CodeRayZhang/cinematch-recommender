let csrfToken = "";
let generation = 0;
let authFailure: (() => void) | undefined;
const pending = new Set<AbortController>();
export function setCsrf(value: string) {
  csrfToken = value;
}
export function onAuthenticationFailure(handler: () => void) {
  authFailure = handler;
}
export function invalidateRequests() {
  generation++;
  pending.forEach((controller) => controller.abort());
  pending.clear();
}
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
  }
}
export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const version = generation;
  const controller = new AbortController();
  const cancel = () => controller.abort();
  if (options.signal?.aborted) controller.abort();
  options.signal?.addEventListener("abort", cancel, { once: true });
  const contextual =
    !/^\/(movies|topics)([/?]|$)/.test(path) ||
    (options.method !== undefined && options.method !== "GET");
  if (contextual) pending.add(controller);
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof URLSearchParams))
    headers.set("Content-Type", "application/json");
  if (csrfToken && options.method && options.method !== "GET")
    headers.set("X-XSRF-TOKEN", csrfToken);
  try {
    const response = await fetch(`/api${path}`, {
      ...options,
      signal: controller.signal,
      credentials: "same-origin",
      headers,
    });
    const data = await response.json().catch(() => null);
    if ((contextual && version !== generation) || controller.signal.aborted)
      throw new DOMException("请求已取消", "AbortError");
    if (!response.ok) {
      if (/^\/(me|admin)(\/|$)/.test(path)) {
        let expired = response.status === 401;
        // CSRF validation can reject an expired session before authorization runs.
        if (response.status === 403) {
          const session = await fetch("/api/session", {
            credentials: "same-origin",
            signal: controller.signal,
          });
          const identity = await session.json();
          if (version !== generation)
            throw new DOMException("请求已取消", "AbortError");
          expired = session.ok && identity.user === null;
        }
        if (expired) authFailure?.();
      }
      throw new ApiError(
        data?.message || "请求未完成，请稍后重试",
        response.status,
      );
    }
    if (data === null)
      throw new ApiError("服务返回了无法识别的内容", response.status);
    return data as T;
  } catch (error) {
    if (error instanceof ApiError || (error as Error).name === "AbortError")
      throw error;
    throw new ApiError("暂时连接不上服务，请稍后重试", 0);
  } finally {
    pending.delete(controller);
    options.signal?.removeEventListener("abort", cancel);
  }
}

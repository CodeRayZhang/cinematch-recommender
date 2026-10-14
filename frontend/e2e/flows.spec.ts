import {
  test,
  expect,
  type Page,
  type APIRequestContext,
} from "@playwright/test";
import { randomUUID } from "node:crypto";
test.beforeEach(async ({ page }) => {
  page.on("pageerror", (error) => {
    throw error;
  });
});
const password = "E2e-user-password-842!";
async function mutation(
  request: APIRequestContext,
  path: string,
  method: string,
  data?: unknown,
) {
  const session = await (await request.get("/api/session")).json();
  const response = await request.fetch(`/api${path}`, {
    method,
    headers: { "X-XSRF-TOKEN": session.csrfToken },
    ...(data === undefined ? {} : { data }),
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response;
}
async function login(
  request: APIRequestContext,
  username: string,
  secret = password,
) {
  const session = await (await request.get("/api/session")).json();
  const response = await request.post("/api/auth/login", {
    headers: { "X-XSRF-TOKEN": session.csrfToken },
    form: { username, password: secret },
  });
  expect(response.ok()).toBeTruthy();
}
async function register(request: APIRequestContext) {
  const username = `browser_${randomUUID().replaceAll("-", "").slice(0, 14)}`;
  await mutation(request, "/auth/register", "POST", {
    username,
    displayName: "自动验收影迷",
    password,
  });
  await login(request, username);
  return username;
}
async function noOverflow(page: Page) {
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth + 1,
    ),
  ).toBeTruthy();
}
test("游客组合筛选、专题与外部海报代理", async ({ page }) => {
  const request = page.request;
  const poster = await request.get("/images/catalog/proxy-check.svg");
  expect(poster.status()).toBe(200);
  expect(await poster.text()).toContain("CineMatch catalog proxy fixture");
  await page.goto("/");
  await expect(page.getByRole("region", { name: "电影专题" })).toBeVisible();
  await page.getByLabel("起始年份", { exact: true }).fill("1990");
  await page.getByLabel("结束年份", { exact: true }).fill("2020");
  await page.getByLabel("片库时长筛选").selectOption("120");
  const response = page.waitForResponse(
    (r) =>
      r.url().includes("/api/movies?") && r.url().includes("yearFrom=1990"),
  );
  await page.getByRole("button", { name: "应用筛选", exact: true }).click();
  const data = await (await response).json();
  expect(data.items.length).toBeGreaterThan(0);
  for (const movie of data.items) {
    expect(movie.releaseYear).toBeGreaterThanOrEqual(1990);
    expect(movie.releaseYear).toBeLessThanOrEqual(2020);
    expect(movie.runtime).toBeLessThanOrEqual(120);
  }
  await expect(page.locator(".movie-card")).toHaveCount(data.items.length);
  await noOverflow(page);
  await page.reload();
  await expect(page.getByLabel("起始年份", { exact: true })).toHaveValue(
    "1990",
  );
});
test("注册、评分、分页片单撤销与个人中心", async ({ page }) => {
  const username = `signup_${randomUUID().replaceAll("-", "").slice(0, 14)}`;
  await page.goto("/");
  await page
    .locator("header")
    .getByRole("button", { name: "登录 / 注册" })
    .click();
  const auth = page.locator("dialog.auth-dialog[open]");
  await auth.getByRole("button", { name: "创建账号", exact: true }).click();
  await auth.getByLabel("怎么称呼你").fill("我的电影时光");
  await auth.getByLabel("账号", { exact: true }).fill(username);
  await auth.getByLabel("密码", { exact: true }).fill(password);
  await auth.getByRole("button", { name: "开始我的观影旅程" }).click();
  await expect(auth).not.toBeVisible();
  await expect(page.getByLabel("账户菜单")).toBeVisible();
  await page.goto("/movie/1");
  await page.getByRole("button", { name: "5 星", exact: true }).click();
  await page
    .getByLabel("我的短评", { exact: true })
    .fill("这一段旅程值得重看。");
  await page.getByRole("button", { name: "保存观影记录" }).click();
  await expect(
    page.getByText("评分已保存，也为你标记了「看过」"),
  ).toBeVisible();
  await page.goto("/library");
  await expect(page.locator(".shelf-row")).toHaveCount(1);
  await page
    .getByRole("button", { name: "移出《星际穿越》", exact: true })
    .click();
  await expect(page.locator(".shelf-row")).toHaveCount(0);
  await page.getByRole("button", { name: "撤销上一次操作" }).click();
  await expect(page.locator(".shelf-row")).toHaveCount(1);
  await page.getByRole("button", { name: "编辑《星际穿越》评分" }).click();
  await expect(page.locator("dialog[open]").getByLabel("我的短评")).toHaveValue(
    "这一段旅程值得重看。",
  );
  await page.getByLabel("关闭评分编辑").click();
  await page.goto("/profile");
  await expect(
    page.getByText("这一段旅程值得重看。", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "推荐效果观察" }),
  ).toBeVisible();
  await noOverflow(page);
});
test("自定义片单创建、备注、排序、封面、导出与深层刷新", async ({ page }) => {
  const request = page.request;
  await register(request);
  await page.goto("/collections");
  await page.getByRole("button", { name: "新建片单", exact: true }).click();
  const editor = page.locator("dialog[open]");
  await editor.getByLabel("片单名称").fill("周末的电影夜");
  await editor.getByLabel("片单介绍").fill("为想重看的故事留一个位置。");
  await editor.getByRole("button", { name: "保存片单" }).click();
  await expect(page).toHaveURL(/\/collections\//);
  await page.getByRole("button", { name: "添加电影", exact: true }).click();
  const search = page.locator("dialog[open]");
  await expect(search.locator(".collection-search-results label")).toHaveCount(
    12,
  );
  await search.locator('input[type="checkbox"]').nth(0).check();
  await search.locator('input[type="checkbox"]').nth(1).check();
  await search.getByRole("button", { name: "加入 2 部电影" }).click();
  await expect(page.locator(".collection-movie-row")).toHaveCount(2);
  const first = page.locator(".collection-movie-row").first();
  const title = await first.locator("h2").innerText();
  await first.locator("textarea").fill("配上爆米花，重看最爱的场景。");
  await first.getByRole("button", { name: "保存备注" }).click();
  await first.getByRole("button", { name: "设为封面" }).click();
  await expect(first.getByRole("button", { name: "当前封面" })).toBeVisible();
  await first.getByRole("button", { name: `下移《${title}》` }).click();
  await expect(
    page.locator(".collection-movie-row").nth(1).locator("h2"),
  ).toHaveText(title);
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "导出 JSON" }).click();
  expect((await download).suggestedFilename()).toMatch(
    /^cinematch-collection-.*\.json$/,
  );
  await page.reload();
  await expect(
    page.locator(".collection-movie-row").nth(1).locator("textarea"),
  ).toHaveValue("配上爆米花，重看最爱的场景。");
  await noOverflow(page);
  await page.screenshot({
    path: `test-results/collections-${test.info().project.name}.png`,
    fullPage: true,
  });
});
test("推荐曝光、筛选、下一组以及停止记录", async ({ page }) => {
  const request = page.request;
  await register(request);
  const event = page.waitForResponse(
    async (response) =>
      response.url().endsWith("/api/me/recommendation-events") &&
      response.request().method() === "POST" &&
      response.ok(),
  );
  await page.goto("/picks");
  await expect(page.locator(".movie-card")).toHaveCount(12);
  await page.locator(".movie-card").first().scrollIntoViewIfNeeded();
  await event;
  const firstIds = await page
    .locator(".movie-card .poster-link")
    .evaluateAll((nodes) => nodes.map((n) => n.getAttribute("href")));
  await page.getByRole("button", { name: "换一组", exact: true }).click();
  await expect(page.locator(".movie-card")).toHaveCount(6);
  const secondIds = await page
    .locator(".movie-card .poster-link")
    .evaluateAll((nodes) => nodes.map((n) => n.getAttribute("href")));
  expect(secondIds.every((id) => !firstIds.includes(id))).toBeTruthy();
  await page.goto("/profile");
  const toggle = page.getByRole("checkbox", {
    name: "允许记录新的推荐曝光与互动",
  });
  await toggle.uncheck();
  await expect(toggle).not.toBeChecked();
  const picks = await (await request.get("/api/recommendations")).json();
  expect(
    picks.every(
      (pick: { requestId: string | null }) => pick.requestId === null,
    ),
  ).toBeTruthy();
});
test("过期会话清理私人状态并可重新登录", async ({ page }) => {
  const request = page.request;
  const username = await register(request);
  await mutation(request, "/me/library/1", "PUT", { status: "WATCHLIST" });
  await page.goto("/library");
  await expect(page.locator(".shelf-row")).toHaveCount(1);
  // Expire the server session without updating the SPA, as another tab could do.
  await mutation(request, "/auth/logout", "POST");
  await page
    .getByRole("button", { name: "移出《星际穿越》", exact: true })
    .click();
  const auth = page.locator("dialog.auth-dialog[open]");
  await expect(auth).toBeVisible();
  await expect(page.locator(".shelf-row")).toHaveCount(0);
  await auth.getByLabel("账号", { exact: true }).fill(username);
  await auth.getByLabel("密码", { exact: true }).fill(password);
  await auth.getByRole("button", { name: "进入我的影院" }).click();
  await expect(auth).not.toBeVisible();
  await expect(page.locator(".shelf-row")).toHaveCount(1);
});
test("管理员维护专题并查看真实指标空值语义", async ({ page }) => {
  const request = page.request;
  await login(request, "e2e_admin", "E2e-only-password-842!");
  await page.goto("/admin");
  await page.getByRole("button", { name: "首页专题", exact: true }).click();
  await page.getByRole("button", { name: "新建专题" }).click();
  await page
    .getByLabel("专题名称", { exact: true })
    .fill(`自动验收专题-${test.info().project.name}`);
  await page.getByLabel("一句话介绍").fill("动画与轻松的周末。");
  await page.getByLabel("电影类型", { exact: true }).selectOption("动画");
  await page.getByRole("button", { name: "保存专题" }).click();
  await expect(
    page.locator(".topic-admin-list").getByRole("heading", {
      name: `自动验收专题-${test.info().project.name}`,
    }),
  ).toBeVisible();
  await page.getByRole("button", { name: "推荐观察", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "推荐效果观察" }),
  ).toBeVisible();
  await expect(page.getByText(/这是行为观察/)).toBeVisible();
  await noOverflow(page);
});

test("切换账号后延迟响应不能恢复前一用户的片单", async ({ page }) => {
  const request = page.request;
  await register(request);
  await mutation(request, "/me/collections", "POST", {
    name: "前一个账号的私密片单",
    description: "",
  });
  await page.goto("/");
  await expect(page.locator(".movie-card")).toHaveCount(12);
  let release!: () => void;
  let captured!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  const started = new Promise<void>((resolve) => {
    captured = resolve;
  });
  await page.route(
    "**/api/me/collections",
    async (route) => {
      const response = await route.fetch();
      captured();
      await gate;
      await route.fulfill({ response }).catch(() => {});
    },
    { times: 1 },
  );
  await page.getByRole("link", { name: "我的片单", exact: true }).click();
  await page.getByRole("link", { name: /自定义片单/ }).click();
  await started;
  await page.getByLabel("账户菜单").click();
  await page.getByRole("button", { name: "退出登录", exact: true }).click();
  await expect(
    page.locator("header").getByRole("button", { name: "登录 / 注册" }),
  ).toBeVisible();
  const second = await register(request);
  await page
    .locator("header")
    .getByRole("button", { name: "登录 / 注册" })
    .click();
  const auth = page.locator("dialog.auth-dialog[open]");
  await auth.getByLabel("账号", { exact: true }).fill(second);
  await auth.getByLabel("密码", { exact: true }).fill(password);
  await auth.getByRole("button", { name: "进入我的影院" }).click();
  await expect(auth).not.toBeVisible();
  release();
  await expect(
    page.getByText("前一个账号的私密片单", { exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "新建片单", exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByText("前一个账号的私密片单", { exact: true }),
  ).toHaveCount(0);
});

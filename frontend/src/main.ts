import { createApp } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import App from "./App.vue";
import DiscoverView from "./views/DiscoverView.vue";
import "./style.css";
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", component: DiscoverView, meta: { title: "发现电影" } },
    {
      path: "/movie/:id",
      component: () => import("./views/DetailView.vue"),
      meta: { title: "电影详情" },
    },
    {
      path: "/picks",
      component: () => import("./views/PicksView.vue"),
      meta: { title: "为你推荐" },
    },
    {
      path: "/library",
      component: () => import("./views/LibraryView.vue"),
      meta: { title: "我的片单" },
    },
    {
      path: "/admin",
      component: () => import("./views/AdminView.vue"),
      meta: { title: "内容管理" },
    },
    {
      path: "/:pathMatch(.*)*",
      component: () => import("./views/NotFoundView.vue"),
      meta: { title: "页面未找到" },
    },
  ],
  scrollBehavior(to, from, saved) {
    if (saved) return saved;
    if (to.path !== from.path) return { top: 0 };
    return undefined;
  },
});
router.afterEach((to) => {
  document.title = `${to.meta.title || "电影"} · CineMatch`;
});
createApp(App).use(router).mount("#app");

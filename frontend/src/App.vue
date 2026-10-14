<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { user, authOpen, toast, loadSession, logout } from "./state";
import Icon from "./components/Icon.vue";
import AuthDialog from "./components/AuthDialog.vue";
const router = useRouter(),
  route = useRoute(),
  search = ref(""),
  menuOpen = ref(false);
watch(
  () => route.query.q,
  (q) => (search.value = String(q || "")),
  { immediate: true },
);
watch(
  () => route.fullPath,
  () => (menuOpen.value = false),
);
function doSearch() {
  router.push({
    path: "/",
    query: search.value.trim() ? { q: search.value.trim() } : {},
  });
}
onMounted(loadSession);
</script>
<template>
  <a class="skip-link" href="#main">跳到主要内容</a>
  <header class="site-header">
    <div class="header-inner">
      <RouterLink to="/" class="brand" aria-label="CineMatch首页"
        ><span class="brand-symbol"><Icon name="Film" :size="24" /></span
        ><span
          >CineMatch<span class="brand-roman">MOVIE RECOMMENDER</span></span
        ></RouterLink
      >
      <nav class="main-nav" aria-label="主要导航">
        <RouterLink
          to="/"
          :class="{
            selected: route.path === '/' || route.path.startsWith('/movie/'),
          }"
          >发现电影</RouterLink
        >
        <RouterLink to="/picks" active-class="selected"
          >为你推荐<span class="nav-dot"></span
        ></RouterLink>
        <RouterLink to="/library" active-class="selected">我的片单</RouterLink>
      </nav>
      <div class="header-actions">
        <form class="search-box" role="search" @submit.prevent="doSearch">
          <Icon name="Search" :size="17" /><input
            v-model="search"
            aria-label="搜索电影、导演"
            placeholder="搜索电影、导演"
            maxlength="100"
          /><kbd>↵</kbd>
        </form>
        <div v-if="user" class="account-menu">
          <button
            class="avatar"
            :aria-expanded="menuOpen"
            aria-label="账户菜单"
            @click="menuOpen = !menuOpen"
          >
            {{ user.displayName.slice(0, 1) }}
          </button>
          <div v-if="menuOpen" class="account-dropdown">
            <strong>{{ user.displayName }}</strong
            ><RouterLink to="/profile"
              ><Icon name="Film" :size="16" />个人中心</RouterLink
            ><RouterLink to="/library"
              ><Icon name="Bookmark" :size="16" />我的片单</RouterLink
            ><RouterLink v-if="user.role === 'ADMIN'" to="/admin"
              ><Icon name="Settings" :size="16" />内容管理</RouterLink
            ><button
              @click="
                logout();
                menuOpen = false;
              "
            >
              <Icon name="LogOut" :size="16" />退出登录
            </button>
          </div>
        </div>
        <button v-else class="login-button" @click="authOpen = true">
          登录 / 注册<Icon name="ArrowUpRight" :size="15" />
        </button>
      </div>
    </div>
  </header>
  <main id="main" tabindex="-1"><RouterView /></main>
  <footer class="site-footer">
    <RouterLink to="/" class="footer-brand"
      >CineMatch<span>让好电影，遇见你。</span></RouterLink
    ><RouterLink to="/sources" class="footer-note">资料与图片来源 ↗</RouterLink
    ><span class="footer-edition"
      >EST. 2018 <span>↗</span> REIMAGINED 2026</span
    >
  </footer>
  <AuthDialog />
  <Transition name="toast"
    ><div v-if="toast" class="toast-message" role="status">
      <Icon name="CheckCircle2" :size="18" />{{ toast }}
    </div></Transition
  >
</template>

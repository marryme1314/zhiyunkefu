<template>
  <div class="shell">
    <header class="top">
      <div class="left">
        <router-link to="/chat" class="brand">
          <span class="logo">智</span>
          <span class="name">智云客服</span>
        </router-link>
        <nav>
          <router-link to="/chat">对话</router-link>
          <router-link v-if="admin" to="/admin">后台</router-link>
          <router-link v-if="admin" to="/knowledge">知识库</router-link>
          <router-link v-if="admin" to="/feedback">反馈</router-link>
        </nav>
      </div>
      <div class="right">
        <button v-if="isChat" class="pill" type="button" @click="newChat">新对话</button>
        <span class="who">{{ admin ? '管理员' : '客户' }}</span>
        <button class="pill ghost" type="button" @click="logout">退出</button>
      </div>
    </header>
    <main class="main">
      <router-view v-slot="{ Component, route: childRoute }">
        <Transition name="page" mode="out-in">
          <component :is="Component" :key="childRoute.path" class="view" />
        </Transition>
      </router-view>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isAdmin } from '../router'

const router = useRouter()
const route = useRoute()
const admin = computed(() => isAdmin())
const isChat = computed(() => route.path.startsWith('/chat'))

function newChat() {
  router.push({ path: '/chat', query: { new: String(Date.now()) } })
}

async function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('role')
  await router.push('/login')
}
</script>

<style scoped>
.shell {
  height: 100%;
  display: grid;
  grid-template-rows: 56px 1fr;
  background: #fbfbfe;
}
.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border-bottom: 1px solid rgba(0,0,0,.04);
  background: rgba(255,255,255,.72);
  backdrop-filter: blur(12px);
}
.left, .right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: inherit;
  text-decoration: none;
  font-weight: 700;
}
.logo {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: #111827;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}
.name { font-size: 15px; letter-spacing: -0.02em; }
nav {
  display: flex;
  gap: 4px;
  margin-left: 8px;
}
nav a {
  color: var(--muted);
  text-decoration: none;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 560;
}
nav a:hover { background: var(--bg-soft); color: var(--ink); }
nav a.router-link-active {
  background: var(--accent-soft);
  color: var(--accent);
}
.who {
  font-size: 12px;
  color: var(--muted);
}
.pill {
  border: 0;
  background: var(--accent-soft);
  color: var(--accent);
  border-radius: 999px;
  padding: 7px 12px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}
.pill:hover { filter: brightness(0.98); }
.pill.ghost {
  background: transparent;
  color: var(--muted);
}
.pill.ghost:hover { background: var(--bg-soft); color: var(--ink); }
.main {
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.view {
  flex: 1;
  min-height: 0;
  height: 100%;
}
.page-enter-active,
.page-leave-active {
  transition: opacity 0.28s ease, transform 0.28s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
@media (max-width: 720px) {
  .name { display: none; }
  nav { margin-left: 0; }
  .who { display: none; }
}
</style>

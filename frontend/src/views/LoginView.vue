<template>
  <div class="scene" :class="{ leaving }">
    <div class="glow glow-a" />
    <div class="glow glow-b" />
    <div class="eclipse" aria-hidden="true">
      <div class="ring" />
      <div class="core" />
      <div class="haze" />
    </div>
    <div class="noise" aria-hidden="true" />
    <div class="flash" aria-hidden="true" />

    <header class="top">
      <div class="brand">智云客服</div>
      <div class="links">
        <button type="button" :class="{ on: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button type="button" :class="{ on: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>
    </header>

    <main class="center">
      <p class="tagline">寻求将知识转化为可靠回答的最优解</p>

      <form class="dock" @submit.prevent="submit">
        <template v-if="mode === 'login'">
          <input v-model="account" class="line" placeholder="邮箱或手机号" autocomplete="username" />
          <input v-model="password" class="line" type="password" placeholder="密码" autocomplete="current-password" />
        </template>
        <template v-else>
          <input v-model="email" class="line" placeholder="邮箱（可选）" />
          <input v-model="phone" class="line" placeholder="手机号（可选）" />
          <input v-model="password" class="line" type="password" placeholder="密码（至少 6 位）" autocomplete="new-password" />
        </template>

        <div class="dock-foot">
          <span class="hint">演示：admin@company.com / Admin123!</span>
          <button class="go" type="submit" :disabled="loading" :title="mode === 'login' ? '登录' : '注册'">
            <span v-if="loading" class="spin" />
            <span v-else>↑</span>
          </button>
        </div>
        <p v-if="error" class="err">{{ error }}</p>
      </form>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../api'

type AuthUser = { id: number; email: string; phone: string; role: string }
type AuthData = { token: string; user: AuthUser }

const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const account = ref('')
const email = ref('')
const phone = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const leaving = ref(false)

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

async function submit() {
  if (leaving.value) return
  error.value = ''
  loading.value = true
  try {
    const data = mode.value === 'login'
      ? await request<AuthData>('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({ account: account.value, password: password.value })
        })
      : await request<AuthData>('/api/auth/register', {
          method: 'POST',
          body: JSON.stringify({ email: email.value, phone: phone.value, password: password.value })
        })
    localStorage.setItem('token', data.token)
    localStorage.setItem('role', data.user?.role || 'USER')
    leaving.value = true
    await wait(520)
    await router.push(data.user?.role === 'ADMIN' ? '/admin' : '/chat')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '失败'
    loading.value = false
    leaving.value = false
  }
}
</script>

<style scoped>
.scene {
  position: relative;
  min-height: 100%;
  overflow: hidden;
  background: #050505;
  color: #f5f5f5;
  display: grid;
  grid-template-rows: auto 1fr;
  color-scheme: dark;
}
.flash {
  position: absolute;
  inset: 0;
  z-index: 5;
  pointer-events: none;
  background: radial-gradient(circle at 50% 40%, rgba(255,255,255,.55), transparent 55%);
  opacity: 0;
}
.scene.leaving .top,
.scene.leaving .center {
  transition: opacity .45s ease, transform .5s ease;
  opacity: 0;
  transform: translateY(10px);
}
.scene.leaving .eclipse {
  animation: eclipse-out .55s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}
.scene.leaving .glow-a {
  transition: opacity .55s ease, transform .55s ease;
  opacity: .9;
  transform: translate(-50%, -50%) scale(1.35);
}
.scene.leaving .flash {
  animation: flash .55s ease forwards;
}
.scene.leaving {
  animation: scene-out .55s ease forwards;
}
@keyframes eclipse-out {
  to {
    transform: translate(-50%, -50%) scale(2.4);
    opacity: 0;
    filter: blur(10px);
  }
}
@keyframes flash {
  0% { opacity: 0; }
  35% { opacity: .35; }
  100% { opacity: 0; }
}
@keyframes scene-out {
  to { opacity: 0; }
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
  opacity: .55;
}
.glow-a {
  width: 520px;
  height: 520px;
  left: 50%;
  top: 28%;
  transform: translate(-50%, -50%);
  background: radial-gradient(circle, rgba(255,255,255,.18), rgba(255,255,255,0) 68%);
}
.glow-b {
  width: 380px;
  height: 380px;
  right: -40px;
  bottom: 10%;
  background: radial-gradient(circle, rgba(120,140,255,.16), transparent 70%);
}

.eclipse {
  position: absolute;
  left: 50%;
  top: 38%;
  width: min(420px, 70vw);
  height: min(420px, 70vw);
  transform: translate(-50%, -50%);
  pointer-events: none;
}
.ring {
  position: absolute;
  inset: 12%;
  border-radius: 50%;
  background:
    radial-gradient(circle at 50% 50%, transparent 54%, rgba(255,255,255,.02) 55%, transparent 62%),
    conic-gradient(from 200deg, transparent 0 62%, rgba(255,255,255,.85) 72%, rgba(255,255,255,.15) 82%, transparent 92%);
  filter: blur(0.2px);
  box-shadow:
    0 0 40px rgba(255,255,255,.12),
    0 0 120px rgba(255,255,255,.08),
    inset 0 0 40px rgba(255,255,255,.05);
  animation: spin 18s linear infinite;
}
.core {
  position: absolute;
  inset: 28%;
  border-radius: 50%;
  background: radial-gradient(circle at 40% 35%, #1a1a1a, #050505 70%);
  box-shadow:
    inset 0 0 40px rgba(0,0,0,.9),
    0 0 60px rgba(0,0,0,.8);
}
.haze {
  position: absolute;
  inset: -8%;
  border-radius: 50%;
  background: radial-gradient(circle, transparent 40%, rgba(255,255,255,.04) 58%, transparent 72%);
  filter: blur(6px);
  animation: pulse 5s ease-in-out infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes pulse {
  0%, 100% { opacity: .55; transform: scale(1); }
  50% { opacity: .9; transform: scale(1.03); }
}

.noise {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: .04;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  mix-blend-mode: soft-light;
}

.top {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 28px;
}
.brand {
  font-weight: 700;
  letter-spacing: 0.04em;
  font-size: 14px;
}
.links {
  display: flex;
  gap: 6px;
  background: rgba(255,255,255,.04);
  border: 1px solid rgba(255,255,255,.08);
  border-radius: 999px;
  padding: 4px;
}
.links button {
  border: 0;
  background: transparent;
  color: rgba(255,255,255,.55);
  border-radius: 999px;
  padding: 7px 14px;
  cursor: pointer;
  font-size: 13px;
}
.links button.on {
  background: rgba(255,255,255,.1);
  color: #fff;
}

.center {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  padding: 24px 16px 10vh;
  gap: 28px;
}
.tagline {
  margin: 0;
  color: rgba(255,255,255,.72);
  font-size: clamp(16px, 2.2vw, 22px);
  letter-spacing: 0.04em;
  text-shadow: 0 0 24px rgba(255,255,255,.18);
}

.dock {
  width: min(560px, 100%);
  background: rgba(22, 22, 22, 0.82);
  border: 1px solid rgba(255,255,255,.1);
  border-radius: 22px;
  padding: 14px 14px 12px;
  backdrop-filter: blur(18px);
  box-shadow:
    0 0 0 1px rgba(255,255,255,.03),
    0 20px 60px rgba(0,0,0,.45),
    inset 0 1px 0 rgba(255,255,255,.05);
}
.line {
  width: 100%;
  border: 0;
  border-radius: 10px;
  background: transparent !important;
  color: #fff !important;
  outline: none;
  padding: 10px 8px;
  font-size: 15px;
  caret-color: #fff;
  box-shadow: none !important;
  -webkit-text-fill-color: #fff;
  border-bottom: 1px solid rgba(255,255,255,.06);
  transition: background .15s ease;
}
.line:last-of-type { border-bottom: 0; }
.line::placeholder {
  color: rgba(255,255,255,.35);
  -webkit-text-fill-color: rgba(255,255,255,.35);
}
.line:focus {
  background: rgba(255,255,255,.04) !important;
}
/* 覆盖 Chrome / Edge 自动填充的浅蓝/白色底 */
.line:-webkit-autofill,
.line:-webkit-autofill:hover,
.line:-webkit-autofill:focus {
  -webkit-text-fill-color: #fff;
  caret-color: #fff;
  box-shadow: 0 0 0 1000px #161616 inset !important;
  transition: background-color 99999s ease-out;
}
.dock-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  padding: 0 4px;
}
.hint {
  font-size: 12px;
  color: rgba(255,255,255,.35);
}
.go {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 999px;
  background: rgba(255,255,255,.92);
  color: #111;
  display: grid;
  place-items: center;
  cursor: pointer;
  font-weight: 700;
  box-shadow: 0 0 24px rgba(255,255,255,.2);
}
.go:disabled { opacity: .45; cursor: not-allowed; }
.spin {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(0,0,0,.2);
  border-top-color: #111;
  border-radius: 50%;
  animation: spin .7s linear infinite;
}
.err {
  color: #fca5a5;
  font-size: 12px;
  margin: 8px 4px 0;
}
</style>

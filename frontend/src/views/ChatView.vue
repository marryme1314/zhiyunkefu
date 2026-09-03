<template>
  <div class="page" :class="{ empty: !messages.length }">
    <div class="bloom bloom-1" aria-hidden="true" />
    <div class="bloom bloom-2" aria-hidden="true" />
    <div class="bloom bloom-3" aria-hidden="true" />
    <button class="hist-toggle" type="button" @click="showHistory = !showHistory">
      {{ showHistory ? '收起会话' : '历史会话' }}
    </button>

    <aside v-if="showHistory" class="history">
      <div class="hist-head">
        <span>历史会话</span>
        <button type="button" @click="createSession">新建</button>
      </div>
      <button
        v-for="s in sessions"
        :key="s.id"
        type="button"
        class="hist-item"
        :class="{ on: s.id === currentId }"
        @click="openSession(s.id); showHistory = false"
      >
        <span>{{ s.title }}</span>
        <em @click.stop="removeSession(s.id)">删除</em>
      </button>
      <div v-if="!sessions.length" class="hist-empty">暂无会话</div>
    </aside>

    <div class="main">
      <div class="messages" ref="scroller">
        <div v-if="!messages.length" class="hero">
          <h1>请描述您的问题</h1>
          <p class="hero-sub">可咨询套餐、账号、退款与售后政策；回答来自企业知识库。</p>
          <div class="modes">
            <button type="button" class="mode" @click="quickAsk('退款怎么申请？')">退款政策</button>
            <button type="button" class="mode" @click="quickAsk('专业版多少钱？')">套餐价格</button>
            <button type="button" class="mode" @click="quickAsk('转人工')">转人工</button>
          </div>
        </div>

        <div
          v-for="m in messages"
          :key="m.key"
          class="turn"
          :class="m.role.toLowerCase()"
        >
          <div class="avatar" :class="m.role.toLowerCase()">
            {{ m.role === 'USER' ? '我' : '智' }}
          </div>
          <div class="body">
            <div class="name">
              {{ m.role === 'USER' ? '你' : '智云客服' }}
              <span v-if="m.role === 'USER' && m.intentLabel" class="tag intent">{{ m.intentLabel }}</span>
              <span v-if="m.status === 'streaming'" class="tag">生成中</span>
              <span v-else-if="m.status === 'failed'" class="tag bad">失败</span>
              <span v-else-if="m.status === 'stopped'" class="tag warn">已中断</span>
            </div>
            <div
              v-if="m.content && m.role === 'ASSISTANT'"
              class="text md"
              v-html="renderMarkdown(m.content)"
            />
            <div v-else-if="m.content" class="text">{{ m.content }}</div>
            <div v-else-if="m.status === 'streaming'" class="text"><span class="blink" /></div>
            <div v-else-if="m.status === 'failed'" class="text">{{ m.error || '生成失败，可重试' }}</div>

            <details
              v-if="m.role === 'ASSISTANT' && m.sources && m.sources.length"
              class="refs"
            >
              <summary>参考资料 · {{ m.sources.length }}</summary>
              <div v-for="(src, i) in m.sources" :key="i" class="ref">
                <b>{{ src.documentName }}</b>
                <p>{{ src.summary }}</p>
              </div>
            </details>

            <div
              v-if="m.role === 'ASSISTANT' && m.suggestions && m.suggestions.length && m.status !== 'streaming'"
              class="suggests"
            >
              <button
                v-for="s in m.suggestions"
                :key="s"
                type="button"
                class="suggest"
                :disabled="sending"
                @click="quickAsk(s)"
              >
                {{ s }}
              </button>
            </div>

            <div class="ops">
              <template v-if="m.role === 'USER'">
                <button type="button" @click="copyText(m.content)">复制</button>
                <button v-if="canRetryUser(m)" type="button" :disabled="sending" @click="retryFromUser(m)">重试</button>
              </template>
              <template v-else>
                <button v-if="m.content" type="button" @click="copyText(m.content)">复制</button>
                <button v-if="canRegenerate(m)" type="button" :disabled="sending" @click="regenerate(m)">重新生成</button>
                <template v-if="m.id > 0 && m.status !== 'streaming'">
                  <button type="button" :class="{ on: m.feedback === 'LIKE' }" @click="feedback(m, 'LIKE')">有用</button>
                  <button type="button" :class="{ on: m.feedback === 'DISLIKE' }" @click="feedback(m, 'DISLIKE')">无用</button>
                </template>
              </template>
            </div>
          </div>
        </div>
      </div>

      <div class="dock">
        <form class="composer" @submit.prevent="send()">
          <textarea
            ref="ta"
            v-model="question"
            rows="1"
            maxlength="500"
            :disabled="sending"
            placeholder="给智云客服发送消息"
            @keydown.enter.exact.prevent="send()"
            @input="autoGrow"
          />
          <div class="bar">
            <div class="chips">
              <span class="chip">{{ question.length }}/500</span>
              <button v-if="sending" type="button" class="chip stop" @click="stopGenerating">停止生成</button>
            </div>
            <button
              class="go"
              type="submit"
              :disabled="sending || !currentId || !question.trim()"
              :title="sending ? '生成中' : '发送'"
            >
              <span v-if="sending" class="sq" />
              <span v-else class="arrow">↑</span>
            </button>
          </div>
        </form>
        <p v-if="error" class="err">{{ error }}</p>
        <p v-else-if="tip" class="tip">{{ tip }}</p>
        <p v-else class="fine">内容由企业知识库检索生成，请以官方政策为准</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request, streamChat, type SourceItem } from '../api'
import { askConfirm } from '../composables/confirm'
import { renderMarkdown } from '../markdown'

type Session = { id: number; title: string }
type MsgStatus = 'ok' | 'streaming' | 'failed' | 'stopped'
type Message = {
  key: string
  id: number
  role: string
  content: string
  sources?: SourceItem[]
  suggestions?: string[]
  feedback?: string
  status?: MsgStatus
  error?: string
  intent?: string
  intentLabel?: string
}

const route = useRoute()
const router = useRouter()
const sessions = ref<Session[]>([])
const currentId = ref<number | null>(null)
const messages = ref<Message[]>([])
const question = ref('')
const sending = ref(false)
const error = ref('')
const tip = ref('')
const showHistory = ref(false)
const scroller = ref<HTMLElement | null>(null)
const ta = ref<HTMLTextAreaElement | null>(null)
const abortRef = ref<AbortController | null>(null)

async function loadSessions() {
  sessions.value = await request('/api/sessions')
}

async function createSession() {
  const s = await request<Session>('/api/sessions', { method: 'POST' })
  sessions.value.unshift(s)
  await openSession(s.id)
}

async function openSession(id: number) {
  if (sending.value) stopGenerating()
  currentId.value = id
  const detail = await request<{ messages: Message[] }>(`/api/sessions/${id}`)
  messages.value = detail.messages.map((m) => ({
    ...m,
    key: 'm-' + m.id,
    status: 'ok' as MsgStatus
  }))
  await scrollBottom()
}

async function removeSession(id: number) {
  const ok = await askConfirm({
    title: '删除会话',
    message: '确认删除该会话？删除后消息记录不可恢复。',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!ok) return
  error.value = ''
  try {
    await request(`/api/sessions/${id}`, { method: 'DELETE' })
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentId.value === id) {
      if (sessions.value.length) await openSession(sessions.value[0].id)
      else await createSession()
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '删除失败'
  }
}

function quickAsk(text: string) {
  question.value = text
  nextTick(() => {
    autoGrow()
    send()
  })
}

function findPrevUser(assistant: Message): Message | null {
  const idx = messages.value.findIndex((m) => m.key === assistant.key)
  for (let i = idx - 1; i >= 0; i--) {
    if (messages.value[i].role === 'USER') return messages.value[i]
  }
  return null
}

function findNextAssistant(user: Message): Message | null {
  const idx = messages.value.findIndex((m) => m.key === user.key)
  for (let i = idx + 1; i < messages.value.length; i++) {
    if (messages.value[i].role === 'ASSISTANT') return messages.value[i]
    if (messages.value[i].role === 'USER') break
  }
  return null
}

function canRetryUser(user: Message) {
  const next = findNextAssistant(user)
  return !next || next.status === 'failed' || (next.status === 'stopped' && !next.content)
}

function canRegenerate(assistant: Message) {
  if (assistant.status === 'streaming') return false
  return Boolean(findPrevUser(assistant))
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    tip.value = '已复制'
    window.setTimeout(() => {
      if (tip.value === '已复制') tip.value = ''
    }, 1600)
  } catch {
    error.value = '复制失败'
  }
}

function stopGenerating() {
  abortRef.value?.abort()
  abortRef.value = null
}

function autoGrow() {
  const el = ta.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

async function send(opts?: {
  questionText?: string
  regenerate?: boolean
  replaceMessageId?: number
  replaceKey?: string
  reuseUser?: boolean
}) {
  if (!currentId.value || sending.value) return
  const q = (opts?.questionText ?? question.value).trim()
  if (!q) return

  error.value = ''
  tip.value = ''
  if (!opts?.questionText) {
    question.value = ''
    nextTick(autoGrow)
  }

  let assistant: Message
  if (opts?.regenerate || opts?.replaceKey) {
    const old =
      (opts.replaceKey ? messages.value.find((m) => m.key === opts.replaceKey) : undefined) ||
      (opts.replaceMessageId ? messages.value.find((m) => m.id === opts.replaceMessageId) : undefined)
    if (old) {
      old.content = ''
      old.sources = []
      old.suggestions = []
      old.status = 'streaming'
      old.error = undefined
      old.feedback = undefined
      assistant = old
    } else {
      assistant = { key: 'a-' + Date.now(), id: 0, role: 'ASSISTANT', content: '', sources: [], suggestions: [], status: 'streaming' }
      messages.value.push(assistant)
    }
  } else {
    if (!opts?.reuseUser) {
      messages.value.push({ key: 'u-' + Date.now(), id: Date.now(), role: 'USER', content: q, status: 'ok' })
    }
    assistant = { key: 'a-' + Date.now(), id: 0, role: 'ASSISTANT', content: '', sources: [], suggestions: [], status: 'streaming' }
    messages.value.push(assistant)
  }

  const controller = new AbortController()
  abortRef.value = controller
  sending.value = true
  await scrollBottom()

  let gotToken = false
  try {
    await streamChat(
      currentId.value,
      q,
      {
        onMeta: (sources, intent, intentLabel) => {
          assistant.sources = sources
          const prevUser = findPrevUser(assistant)
          if (prevUser) {
            prevUser.intent = intent || prevUser.intent
            prevUser.intentLabel = intentLabel || prevUser.intentLabel
          }
        },
        onToken: (text) => {
          gotToken = true
          assistant.content += text
          scheduleScroll()
        },
        onDone: (messageId, interrupted, suggestions) => {
          assistant.id = messageId
          assistant.status = interrupted ? 'stopped' : 'ok'
          assistant.suggestions = suggestions || []
          loadSessions()
        },
        onError: (message) => {
          if (gotToken || assistant.content) {
            assistant.status = 'stopped'
            if (!assistant.content.includes('已中断')) {
              assistant.content = (assistant.content || '') + (assistant.content ? '\n\n' : '') + '（已中断）'
            }
            tip.value = '已停止生成'
          } else {
            assistant.status = 'failed'
            assistant.error = message
            assistant.content = ''
            if (message !== '已停止生成') error.value = message
          }
        }
      },
      {
        regenerate: Boolean(opts?.regenerate || opts?.replaceKey || opts?.replaceMessageId),
        replaceMessageId: opts?.replaceMessageId && opts.replaceMessageId > 0 ? opts.replaceMessageId : undefined,
        signal: controller.signal
      }
    )
    if (assistant.status === 'streaming') {
      assistant.status = assistant.content ? 'ok' : 'failed'
      if (!assistant.content) assistant.error = '未收到回复'
    }
  } catch (e) {
    if (controller.signal.aborted) {
      assistant.status = gotToken ? 'stopped' : 'failed'
      if (!gotToken) assistant.error = '已停止生成'
    } else {
      assistant.status = 'failed'
      assistant.error = e instanceof Error ? e.message : '发送失败'
      error.value = assistant.error
    }
  } finally {
    if (abortRef.value === controller) abortRef.value = null
    sending.value = false
  }
}

async function regenerate(assistant: Message) {
  const user = findPrevUser(assistant)
  if (!user || sending.value) return
  await send({
    questionText: user.content,
    regenerate: true,
    replaceKey: assistant.key,
    replaceMessageId: assistant.id > 0 ? assistant.id : undefined
  })
}

async function retryFromUser(user: Message) {
  if (sending.value) return
  const next = findNextAssistant(user)
  if (next) {
    await send({
      questionText: user.content,
      regenerate: true,
      replaceKey: next.key,
      replaceMessageId: next.id > 0 ? next.id : undefined
    })
    return
  }
  await send({ questionText: user.content, reuseUser: true })
}

async function feedback(message: Message, type: string) {
  try {
    const comment = window.prompt('可选：填写文字反馈（可留空）', '') || ''
    await request(`/api/messages/${message.id}/feedback`, {
      method: 'POST',
      body: JSON.stringify({ type, comment })
    })
    message.feedback = type
    tip.value = type === 'LIKE' ? '已记录为「有用」' : '已记录为「无用」'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '反馈失败'
  }
}

let scrollTimer = 0
function scheduleScroll() {
  if (scrollTimer) return
  scrollTimer = window.requestAnimationFrame(() => {
    scrollTimer = 0
    scrollBottom()
  })
}

async function scrollBottom() {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}

watch(
  () => route.query.new,
  async (v) => {
    if (!v) return
    try {
      await createSession()
      router.replace({ path: '/chat', query: {} })
    } catch (e) {
      error.value = e instanceof Error ? e.message : '新建失败'
    }
  }
)

onMounted(async () => {
  try {
    await loadSessions()
    if (route.query.new) {
      await createSession()
      router.replace({ path: '/chat', query: {} })
      return
    }
    if (sessions.value.length) await openSession(sessions.value[0].id)
    else await createSession()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载会话失败'
  }
})

onUnmounted(() => stopGenerating())
</script>

<style scoped>
.page {
  position: relative;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fbfbfe;
}
.bloom {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(70px);
  z-index: 0;
}
.bloom-1 {
  width: 520px;
  height: 360px;
  left: 50%;
  top: -80px;
  transform: translateX(-50%);
  background: radial-gradient(circle, rgba(125, 155, 255, 0.28), transparent 70%);
}
.bloom-2 {
  width: 340px;
  height: 340px;
  left: 12%;
  bottom: 18%;
  background: radial-gradient(circle, rgba(180, 200, 255, 0.18), transparent 70%);
  animation: drift 12s ease-in-out infinite;
}
.bloom-3 {
  width: 300px;
  height: 300px;
  right: 10%;
  bottom: 22%;
  background: radial-gradient(circle, rgba(99, 120, 255, 0.14), transparent 70%);
  animation: drift 14s ease-in-out infinite reverse;
}
@keyframes drift {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(18px, -14px); }
}
.main {
  position: relative;
  z-index: 1;
}

.hist-toggle {
  position: absolute;
  top: 14px;
  left: 18px;
  z-index: 5;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 999px;
  padding: 7px 12px;
  font-size: 12px;
  color: var(--muted);
  cursor: pointer;
  box-shadow: var(--shadow);
}
.hist-toggle:hover { color: var(--ink); }

.history {
  position: absolute;
  top: 52px;
  left: 18px;
  z-index: 6;
  width: 260px;
  max-height: min(70vh, 480px);
  overflow: auto;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 18px;
  box-shadow: var(--shadow-lg);
  padding: 10px;
}
.hist-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px 10px;
  font-size: 13px;
  font-weight: 650;
}
.hist-head button {
  border: 0;
  background: var(--accent-soft);
  color: var(--accent);
  border-radius: 999px;
  padding: 4px 10px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}
.hist-item {
  width: 100%;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 10px 10px;
  border-radius: 12px;
  cursor: pointer;
  font-size: 13px;
  color: #444;
}
.hist-item:hover, .hist-item.on { background: var(--bg-soft); }
.hist-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hist-item em {
  font-style: normal;
  color: var(--muted);
  font-size: 12px;
  opacity: 0;
}
.hist-item:hover em { opacity: 1; }
.hist-item em:hover { color: var(--danger); }
.hist-empty {
  color: var(--muted);
  font-size: 13px;
  padding: 18px 8px;
  text-align: center;
}

.main {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  width: min(820px, 100%);
  margin: 0 auto;
}

.messages {
  overflow: auto;
  padding: 24px 20px 8px;
  min-height: 0;
}

.page.empty .messages {
  display: flex;
  align-items: center;
  justify-content: center;
}
.page.empty .dock {
  position: relative;
  top: -10vh;
}
.page.empty .composer {
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.85) inset,
    0 18px 60px rgba(77, 107, 254, 0.16),
    0 4px 16px rgba(15, 23, 42, 0.05);
}

.hero {
  width: 100%;
  text-align: center;
  padding: 20px 12px 8px;
}
.hero h1 {
  margin: 0;
  font-size: clamp(22px, 2.8vw, 28px);
  font-weight: 650;
  letter-spacing: -0.02em;
  color: #111827;
}
.hero-sub {
  margin: 10px auto 0;
  max-width: 420px;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.6;
}
.modes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 20px;
}
.mode {
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #374151;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
}
.mode:hover {
  border-color: #d1d5db;
  background: #f9fafb;
}

.turn {
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 12px;
  padding: 16px 4px;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
}
.avatar.assistant {
  background: linear-gradient(135deg, #4d6bfe, #7c8cff);
  color: #fff;
}
.avatar.user { background: #e8eaf5; color: #445; }
.name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 650;
  margin-bottom: 6px;
}
.tag {
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 999px;
  padding: 1px 8px;
}
.tag.intent { background: #eef2ff; color: #3730a3; }
.tag.bad { background: #fee2e2; color: #b91c1c; }
.tag.warn { background: #ffedd5; color: #c2410c; }
.text {
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 15px;
}
.text.md {
  white-space: normal;
}
.text.md :deep(p) { margin: 0 0 0.65em; }
.text.md :deep(p:last-child) { margin-bottom: 0; }
.text.md :deep(h1),
.text.md :deep(h2),
.text.md :deep(h3) {
  margin: 0.8em 0 0.4em;
  font-size: 1.05em;
  font-weight: 700;
  line-height: 1.4;
}
.text.md :deep(ul) {
  margin: 0.4em 0 0.7em;
  padding-left: 1.25em;
}
.text.md :deep(li) { margin: 0.2em 0; }
.text.md :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0.35em 0.8em;
  border-left: 3px solid var(--line);
  color: var(--muted);
}
.text.md :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.92em;
  background: #f1f5f9;
  padding: 0.1em 0.35em;
  border-radius: 6px;
}
.text.md :deep(pre) {
  margin: 0.6em 0;
  padding: 10px 12px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 12px;
  overflow: auto;
}
.text.md :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
}
.turn.user .text {
  display: inline-block;
  background: #f5f6fb;
  border-radius: 16px;
  padding: 10px 14px;
}
.blink {
  display: inline-block;
  width: 8px;
  height: 16px;
  border-radius: 2px;
  background: var(--accent);
  animation: blink 1s step-end infinite;
  vertical-align: text-bottom;
}
@keyframes blink { 50% { opacity: 0; } }

.refs {
  margin-top: 10px;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 8px 12px;
  background: #fafbff;
  font-size: 13px;
}
.refs summary {
  cursor: pointer;
  color: var(--muted);
  font-weight: 600;
  user-select: none;
}
.ref { margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--line); }
.ref b { display: block; margin-bottom: 2px; }
.ref p { margin: 0; color: var(--muted); white-space: pre-wrap; }

.suggests {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.suggest {
  border: 1px solid #dbe4ff;
  background: #f4f7ff;
  color: #31408a;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}
.suggest:hover:not(:disabled) { background: #e8eeff; }
.suggest:disabled { opacity: 0.5; cursor: not-allowed; }

.ops {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
}
.ops button {
  border: 0;
  background: transparent;
  color: var(--muted);
  border-radius: 8px;
  padding: 4px 8px;
  cursor: pointer;
  font-size: 12px;
}
.ops button:hover { background: #f3f4f8; color: var(--ink); }
.ops button:disabled { opacity: .4; cursor: not-allowed; }
.ops button.on { color: var(--accent); background: var(--accent-soft); }

.dock {
  padding: 8px 18px 22px;
}
.composer {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(120, 140, 255, 0.14);
  border-radius: 26px;
  padding: 14px 14px 12px;
  backdrop-filter: blur(16px);
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.8) inset,
    0 10px 40px rgba(77, 107, 254, 0.12),
    0 2px 10px rgba(15, 23, 42, 0.04);
}
.composer textarea {
  width: 100%;
  border: 0;
  outline: none;
  resize: none;
  background: transparent;
  min-height: 28px;
  max-height: 160px;
  line-height: 1.55;
  padding: 0 4px 10px;
}
.composer textarea:disabled { opacity: .65; }
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}
.chips { display: flex; gap: 8px; align-items: center; }
.chip {
  border: 0;
  background: #f3f5ff;
  color: #667;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
}
.chip.stop {
  cursor: pointer;
  background: #fff7ed;
  color: #c2410c;
}
.go {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  display: grid;
  place-items: center;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(77, 107, 254, 0.35);
}
.go:disabled {
  background: #d7dcf5;
  box-shadow: none;
  cursor: not-allowed;
}
.arrow { font-size: 16px; font-weight: 700; line-height: 1; }
.sq {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: #fff;
}
.fine, .tip {
  text-align: center;
  font-size: 12px;
  color: var(--muted);
  margin: 12px 0 0;
}
.tip { color: var(--ok); }
.err { text-align: center; margin: 12px 0 0; }
</style>

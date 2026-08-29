<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>管理后台</h2>
        <p>用户管理、全量会话、反馈统计与日均问答量</p>
      </div>
      <button class="btn ghost" type="button" @click="load">刷新</button>
    </div>

    <p v-if="error" class="err">{{ error }}</p>
    <p v-if="tip" class="ok-tip">{{ tip }}</p>

    <div class="kpis">
      <div class="kpi"><span>会话总数</span><strong>{{ overview.sessionCount }}</strong></div>
      <div class="kpi"><span>累计提问</span><strong>{{ overview.questionCount }}</strong></div>
      <div class="kpi"><span>今日提问</span><strong>{{ overview.todayQuestionCount }}</strong></div>
      <div class="kpi"><span>反馈总数</span><strong>{{ overview.feedbackCount }}</strong></div>
    </div>

    <div class="grid">
      <section class="panel-card chart-card">
        <div class="panel-title">近 {{ days }} 日问答量</div>
        <svg v-if="points.length" class="chart" viewBox="0 0 640 220" role="img">
          <polyline
            fill="none"
            stroke="#4d6bfe"
            stroke-width="2.5"
            :points="linePoints"
          />
          <polygon
            :points="areaPoints"
            fill="rgba(77,107,254,0.12)"
          />
          <g v-for="(p, i) in plot" :key="p.date">
            <circle :cx="p.x" :cy="p.y" r="3.5" fill="#4d6bfe" />
            <text
              v-if="i % labelStep === 0 || i === plot.length - 1"
              :x="p.x"
              y="210"
              text-anchor="middle"
              class="axis"
            >{{ p.date.slice(5) }}</text>
          </g>
        </svg>
        <div v-else class="empty">暂无数据</div>
      </section>

      <section class="panel-card">
        <div class="panel-title">反馈统计</div>
        <div class="fb-row">
          <div>
            <div class="fb-label">有用</div>
            <div class="fb-num like">{{ overview.feedbackStats.like }}</div>
          </div>
          <div>
            <div class="fb-label">无用</div>
            <div class="fb-num dislike">{{ overview.feedbackStats.dislike }}</div>
          </div>
        </div>
        <div class="bar">
          <div class="like-bar" :style="{ width: likePct + '%' }" />
        </div>
        <p class="hint">有用占比 {{ likePct }}%</p>
        <router-link class="link" to="/feedback">查看反馈明细 →</router-link>
      </section>

      <section class="panel-card">
        <div class="panel-title">意图分布</div>
        <div v-if="!overview.intentStats.length" class="empty">暂无意图标注</div>
        <div v-for="item in overview.intentStats" :key="item.intent || item.label" class="intent-row">
          <span>{{ item.label || '未标注' }}</span>
          <div class="track"><i :style="{ width: intentWidth(item.count) }" /></div>
          <em>{{ item.count }}</em>
        </div>
      </section>
    </div>

    <section class="panel-card table-card">
      <div class="panel-title">用户账号管理</div>
      <p class="section-hint">删除账号会同步清理其会话、消息与反馈；内置管理员与当前登录账号不可删。</p>
      <table>
        <thead>
          <tr>
            <th>账号</th>
            <th>角色</th>
            <th>会话 / 提问</th>
            <th>注册时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!users.length">
            <td colspan="5" class="empty">暂无用户</td>
          </tr>
          <tr v-for="u in users" :key="u.id">
            <td>
              <div class="title">{{ u.account }}</div>
              <div class="sub">
                #{{ u.id }}
                <span v-if="u.self"> · 当前登录</span>
                <span v-if="u.builtInAdmin"> · 内置管理员</span>
              </div>
            </td>
            <td>
              <span class="badge" :class="u.role === 'ADMIN' ? 'like' : 'muted'">
                {{ u.role === 'ADMIN' ? '管理员' : '客户' }}
              </span>
            </td>
            <td>{{ u.sessionCount }} / {{ u.questionCount }}</td>
            <td class="sub">{{ formatTime(u.createdAt) }}</td>
            <td class="actions">
              <button
                class="btn danger mini"
                type="button"
                :disabled="!u.canDelete || deletingId === u.id"
                :title="u.canDelete ? '删除该用户及其全部数据' : u.deleteBlockReason"
                @click="confirmDelete(u)"
              >
                {{ deletingId === u.id ? '删除中…' : '删除' }}
              </button>
              <div v-if="!u.canDelete" class="block-reason">{{ u.deleteBlockReason }}</div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="panel-card table-card">
      <div class="panel-title">全量会话记录</div>
      <table>
        <thead>
          <tr>
            <th>会话</th>
            <th>用户</th>
            <th>提问数</th>
            <th>最近意图</th>
            <th>更新时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!sessions.length">
            <td colspan="6" class="empty">暂无会话</td>
          </tr>
          <tr v-for="s in sessions" :key="s.id">
            <td>
              <div class="title">{{ s.title }}</div>
              <div class="sub">#{{ s.id }} · {{ s.messageCount }} 条消息</div>
            </td>
            <td>{{ accountOf(s) }}</td>
            <td>{{ s.questionCount }}</td>
            <td>
              <span v-if="s.lastIntentLabel" class="badge muted">{{ s.lastIntentLabel }}</span>
              <span v-else class="sub">—</span>
            </td>
            <td class="sub">{{ formatTime(s.updatedAt) }}</td>
            <td><button class="btn ghost mini" type="button" @click="openDetail(s.id)">查看</button></td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="detail" class="drawer" @click.self="detail = null">
      <div class="drawer-panel">
        <div class="drawer-head">
          <div>
            <h3>{{ detail.session.title }}</h3>
            <p>{{ accountDetail }} · 更新于 {{ formatTime(detail.session.updatedAt) }}</p>
          </div>
          <button class="btn ghost mini" type="button" @click="detail = null">关闭</button>
        </div>
        <div class="drawer-body">
          <div v-for="m in detail.messages" :key="m.id" class="msg" :class="m.role.toLowerCase()">
            <div class="msg-meta">
              <strong>{{ m.role === 'USER' ? '用户' : '客服' }}</strong>
              <span v-if="m.intentLabel" class="badge muted">{{ m.intentLabel }}</span>
              <span class="sub">{{ formatTime(m.createdAt) }}</span>
            </div>
            <pre>{{ m.content }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { request } from '../api'
import { askConfirm } from '../composables/confirm'

type DailyPoint = { date: string; count: number }
type IntentStat = { intent: string; label: string; count: number }
type Overview = {
  sessionCount: number
  questionCount: number
  todayQuestionCount: number
  feedbackCount: number
  dailyQuestions: DailyPoint[]
  feedbackStats: { like: number; dislike: number; total: number }
  intentStats: IntentStat[]
}
type SessionRow = {
  id: number
  title: string
  updatedAt: string
  messageCount: number
  questionCount: number
  lastIntentLabel: string
  userEmail: string
  userPhone: string
}
type Detail = {
  session: { title: string; updatedAt: string; userEmail: string; userPhone: string }
  messages: { id: number; role: string; content: string; createdAt: string; intentLabel: string }[]
}
type UserRow = {
  id: number
  account: string
  role: string
  createdAt: string
  sessionCount: number
  questionCount: number
  builtInAdmin: boolean
  self: boolean
  canDelete: boolean
  deleteBlockReason: string
}

const days = 14
const error = ref('')
const tip = ref('')
const sessions = ref<SessionRow[]>([])
const users = ref<UserRow[]>([])
const deletingId = ref<number | null>(null)
const detail = ref<Detail | null>(null)
const overview = ref<Overview>({
  sessionCount: 0,
  questionCount: 0,
  todayQuestionCount: 0,
  feedbackCount: 0,
  dailyQuestions: [],
  feedbackStats: { like: 0, dislike: 0, total: 0 },
  intentStats: []
})

const points = computed(() => overview.value.dailyQuestions || [])
const maxCount = computed(() => Math.max(1, ...points.value.map((p) => p.count)))
const plot = computed(() => {
  const list = points.value
  if (!list.length) return []
  const w = 600
  const h = 160
  const left = 20
  const top = 20
  return list.map((p, i) => {
    const x = left + (list.length === 1 ? w / 2 : (i / (list.length - 1)) * w)
    const y = top + h - (p.count / maxCount.value) * h
    return { ...p, x, y }
  })
})
const linePoints = computed(() => plot.value.map((p) => `${p.x},${p.y}`).join(' '))
const areaPoints = computed(() => {
  if (!plot.value.length) return ''
  const first = plot.value[0]
  const last = plot.value[plot.value.length - 1]
  return `${first.x},180 ` + linePoints.value + ` ${last.x},180`
})
const labelStep = computed(() => Math.max(1, Math.ceil(points.value.length / 7)))
const likePct = computed(() => {
  const total = overview.value.feedbackStats.total || 0
  if (!total) return 0
  return Math.round((overview.value.feedbackStats.like / total) * 100)
})
const intentMax = computed(() => Math.max(1, ...overview.value.intentStats.map((i) => i.count)))
const accountDetail = computed(() => {
  if (!detail.value) return ''
  return detail.value.session.userEmail || detail.value.session.userPhone || '未知用户'
})

function intentWidth(count: number) {
  return Math.max(8, Math.round((count / intentMax.value) * 100)) + '%'
}

function accountOf(s: SessionRow) {
  return s.userEmail || s.userPhone || '未知用户'
}

function formatTime(value: string) {
  return value.replace('T', ' ').slice(0, 16)
}

async function load() {
  error.value = ''
  try {
    const [ov, list, userList] = await Promise.all([
      request<Overview>(`/api/admin/overview?days=${days}`),
      request<SessionRow[]>('/api/admin/sessions'),
      request<UserRow[]>('/api/admin/users')
    ])
    overview.value = ov
    sessions.value = list
    users.value = userList
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

async function openDetail(id: number) {
  error.value = ''
  try {
    detail.value = await request<Detail>(`/api/admin/sessions/${id}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载会话失败'
  }
}

async function confirmDelete(u: UserRow) {
  if (!u.canDelete || deletingId.value) return
  const ok = await askConfirm({
    title: '删除账号',
    message:
      `确认删除账号「${u.account}」？\n\n将永久删除：\n· ${u.sessionCount} 个会话\n· ${u.questionCount} 次提问及相关消息\n· 该账号产生的反馈数据\n\n此操作不可恢复。`,
    confirmText: '确认删除',
    cancelText: '取消'
  })
  if (!ok) return
  error.value = ''
  tip.value = ''
  deletingId.value = u.id
  try {
    const result = await request<{
      account: string
      deletedSessions: number
      deletedMessages: number
      deletedFeedbacks: number
      message: string
    }>(`/api/admin/users/${u.id}`, { method: 'DELETE' })
    tip.value = `${result.message}（会话 ${result.deletedSessions} / 消息 ${result.deletedMessages} / 反馈 ${result.deletedFeedbacks}）`
    if (detail.value) detail.value = null
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    deletingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
  max-width: 1100px;
}
.kpi {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: var(--shadow);
}
.kpi span { display: block; color: var(--muted); font-size: 12px; }
.kpi strong { display: block; margin-top: 6px; font-size: 24px; letter-spacing: -0.03em; }
.grid {
  display: grid;
  grid-template-columns: 1.4fr 0.8fr 0.9fr;
  gap: 12px;
  margin-bottom: 16px;
  max-width: 1100px;
}
.panel-card { padding: 16px; }
.panel-title { font-weight: 700; margin-bottom: 12px; }
.chart { width: 100%; height: 220px; display: block; }
.axis { fill: #9ca3af; font-size: 10px; }
.fb-row { display: flex; gap: 24px; margin-bottom: 12px; }
.fb-label { color: var(--muted); font-size: 12px; }
.fb-num { font-size: 28px; font-weight: 700; margin-top: 4px; }
.fb-num.like { color: #15803d; }
.fb-num.dislike { color: #b91c1c; }
.bar {
  height: 8px;
  background: #fee2e2;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 8px;
}
.like-bar { height: 100%; background: #86efac; }
.link { font-size: 13px; color: var(--accent); }
.intent-row {
  display: grid;
  grid-template-columns: 72px 1fr 28px;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
}
.track {
  height: 8px;
  background: #f3f4f8;
  border-radius: 999px;
  overflow: hidden;
}
.track i {
  display: block;
  height: 100%;
  background: #4d6bfe;
  border-radius: 999px;
}
.intent-row em { font-style: normal; color: var(--muted); text-align: right; }
.table-card { max-width: 1100px; padding: 0; overflow: auto; margin-bottom: 16px; }
.table-card .panel-title { padding: 16px 16px 0; }
.section-hint {
  margin: 4px 16px 0;
  color: var(--muted);
  font-size: 12px;
}
.actions { min-width: 140px; }
.block-reason {
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
  max-width: 180px;
  line-height: 1.4;
}
.ok-tip { color: var(--ok); font-size: 13px; margin: 0 0 12px; }
table { width: 100%; border-collapse: collapse; }
th, td { text-align: left; padding: 12px 16px; border-top: 1px solid var(--line); vertical-align: top; }
th { font-size: 12px; color: var(--muted); background: #fafafa; }
.title { font-weight: 600; }
.sub { color: var(--muted); font-size: 12px; margin-top: 2px; }
.mini { padding: 5px 10px; font-size: 12px; }
.empty { text-align: center; color: var(--muted); padding: 24px; }
.drawer {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  display: grid;
  place-items: center;
  z-index: 40;
  padding: 20px;
}
.drawer-panel {
  width: min(720px, 100%);
  max-height: min(80vh, 760px);
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 50px rgba(0,0,0,.18);
}
.drawer-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line);
}
.drawer-head h3 { margin: 0; font-size: 16px; }
.drawer-head p { margin: 4px 0 0; color: var(--muted); font-size: 12px; }
.drawer-body { overflow: auto; padding: 14px 18px 18px; }
.msg { margin-bottom: 14px; }
.msg-meta { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; font-size: 12px; }
.msg pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f8fafc;
  border-radius: 10px;
  padding: 10px 12px;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
}
.msg.user pre { background: #eef2ff; }
@media (max-width: 1000px) {
  .kpis, .grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 700px) {
  .kpis, .grid { grid-template-columns: 1fr; }
}
</style>

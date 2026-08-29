<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>回答反馈</h2>
        <p>查看客户对 AI 回答的「有用 / 无用」评价与文字说明</p>
      </div>
      <button class="btn ghost-dark" @click="load">刷新</button>
    </div>
    <p v-if="error" class="err">{{ error }}</p>
    <div v-if="!items.length && !error" class="empty panel-card">暂无反馈。客户在对话里点「无用 / 有用」后会出现在这里。</div>
    <div v-else class="list">
      <article v-for="item in items" :key="item.id" class="panel-card item">
        <div class="top">
          <span class="badge" :class="item.type === 'LIKE' ? 'like' : 'dislike'">
            {{ item.type === 'LIKE' ? '有用' : '无用' }}
          </span>
          <span class="meta">{{ item.createdAt }} · {{ accountOf(item) }} · 会话「{{ item.sessionTitle }}」</span>
        </div>
        <div class="block">
          <div class="label">客户问题</div>
          <div class="text">{{ item.question || '（未找到对应问题）' }}</div>
        </div>
        <div class="block">
          <div class="label">AI 回答摘要</div>
          <div class="text">{{ item.answer }}</div>
        </div>
        <div v-if="item.comment" class="block comment">
          <div class="label">文字反馈</div>
          <div class="text">{{ item.comment }}</div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { request } from '../api'

type FeedbackItem = {
  id: number
  type: string
  comment: string
  createdAt: string
  userEmail: string
  userPhone: string
  sessionTitle: string
  question: string
  answer: string
}

const items = ref<FeedbackItem[]>([])
const error = ref('')

function accountOf(item: FeedbackItem) {
  return item.userEmail || item.userPhone || '未知用户'
}

async function load() {
  error.value = ''
  try {
    items.value = await request('/api/admin/feedbacks')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

onMounted(load)
</script>

<style scoped>
.ghost-dark {
  background: #fff;
  color: var(--ink);
  border: 1px solid var(--line);
}
.list { display: grid; gap: 14px; }
.item { padding: 18px 18px 14px; }
.top { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 12px; }
.meta { color: var(--muted); font-size: 12px; }
.block { margin-bottom: 10px; }
.label { font-size: 12px; color: var(--muted); margin-bottom: 4px; }
.text { font-size: 14px; line-height: 1.6; white-space: pre-wrap; }
.comment {
  background: #f8fafc;
  border-radius: 12px;
  padding: 10px 12px;
}
.empty { padding: 28px; color: var(--muted); text-align: center; }
</style>

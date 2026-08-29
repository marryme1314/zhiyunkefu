<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>知识库</h2>
        <p>支持 .txt / .md / .pdf / .docx / .doc。上传时可选知识库分区，检索会按意图优先该分区。</p>
      </div>
      <div class="upload-row">
        <select v-model="collection">
          <option value="AUTO">分区：按文件名自动</option>
          <option value="PRODUCT">产品</option>
          <option value="AFTER_SALES">售后政策</option>
          <option value="FAQ">常见问题</option>
          <option value="GENERAL">通用</option>
        </select>
        <label class="btn">
          上传 txt / md / pdf / word
          <input
          type="file"
          accept=".txt,.md,.pdf,.doc,.docx,text/plain,text/markdown,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          hidden
          @change="onFile"
        />
      </label>
      </div>
    </div>
    <p v-if="error" class="err">{{ error }}</p>
    <div class="panel-card">
      <table>
        <thead>
          <tr><th>文档</th><th>知识库</th><th>状态</th><th>上传时间</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="!docs.length">
            <td colspan="5" class="empty">暂无文档</td>
          </tr>
          <tr v-for="d in docs" :key="d.id">
            <td class="name">{{ d.filename }}</td>
            <td class="time">{{ d.collectionLabel || d.collection || '通用' }}</td>
            <td>
              <span class="badge" :class="badgeClass(d.status)">{{ statusText(d.status) }}</span>
              <div v-if="d.status === 'FAILED' && d.errorMessage" class="fail">{{ d.errorMessage }}</div>
            </td>
            <td class="time">{{ d.createdAt }}</td>
            <td><button class="btn danger mini" @click="remove(d.id)">删除</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <p class="hint foot">删除文档时会同步清除向量切片。首次启动会自动导入 3 篇示例文档，客户无需上传也能提问。</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { request } from '../api'
import { askConfirm } from '../composables/confirm'

type Doc = {
  id: number
  filename: string
  status: string
  createdAt: string
  errorMessage?: string
  collection?: string
  collectionLabel?: string
}
const docs = ref<Doc[]>([])
const collection = ref('AUTO')
const error = ref('')

async function load() {
  docs.value = await request('/api/knowledge/documents')
}

async function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  error.value = ''
  const form = new FormData()
  form.append('file', file)
  form.append('collection', collection.value)
  try {
    await request('/api/knowledge/documents', { method: 'POST', body: form })
    await load()
    startPoll()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '上传失败'
  }
}

async function remove(id: number) {
  const ok = await askConfirm({
    title: '删除文档',
    message: '确认删除该文档及向量数据？删除后不可恢复。',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!ok) return
  await request(`/api/knowledge/documents/${id}`, { method: 'DELETE' })
  await load()
}

function statusText(status: string) {
  if (status === 'READY') return '就绪'
  if (status === 'PROCESSING') return '处理中'
  if (status === 'FAILED') return '失败'
  return status
}

function badgeClass(status: string) {
  if (status === 'READY') return 'ok'
  if (status === 'PROCESSING') return 'warn'
  if (status === 'FAILED') return 'fail'
  return 'muted'
}

let timer = 0
function startPoll() {
  if (timer) return
  timer = window.setInterval(async () => {
    await load()
    if (!docs.value.some((d) => d.status === 'PROCESSING')) {
      window.clearInterval(timer)
      timer = 0
    }
  }, 1500)
}

onMounted(() => {
  load().then(() => {
    if (docs.value.some((d) => d.status === 'PROCESSING')) startPoll()
  })
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
table { width: 100%; border-collapse: collapse; }
th, td { text-align: left; padding: 14px 16px; border-bottom: 1px solid var(--line); vertical-align: top; }
th { font-size: 12px; color: var(--muted); background: #fafaf9; font-weight: 650; }
.name { font-weight: 600; }
.time { color: var(--muted); font-size: 13px; white-space: nowrap; }
.fail { color: var(--danger); font-size: 12px; margin-top: 6px; max-width: 360px; }
.empty { text-align: center; color: var(--muted); padding: 28px !important; }
.mini { padding: 6px 12px; font-size: 12px; }
.foot { margin-top: 14px; }
.upload-row { display: flex; align-items: center; gap: 10px; }
.upload-row select {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff;
  color: var(--ink);
}
</style>

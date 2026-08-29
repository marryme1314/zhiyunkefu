export type ApiResponse<T> = { code: number; message: string; data: T }

const BASE = ''

export function getToken(): string {
  return localStorage.getItem('token') || ''
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (!(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const res = await fetch(BASE + path, { ...init, headers })
  const text = await res.text()
  let json: ApiResponse<T>
  try {
    json = JSON.parse(text) as ApiResponse<T>
  } catch {
    throw new Error(text || `请求失败（HTTP ${res.status}）`)
  }
  if (!res.ok || json.code !== 0) {
    throw new Error(json.message || '请求失败')
  }
  return json.data
}

export type StreamOptions = {
  regenerate?: boolean
  replaceMessageId?: number
  signal?: AbortSignal
}

export async function streamChat(
  sessionId: number,
  question: string,
  handlers: {
    onMeta: (sources: SourceItem[], intent?: string, intentLabel?: string) => void
    onToken: (text: string) => void
    onDone: (messageId: number, interrupted?: boolean, suggestions?: string[]) => void
    onError: (message: string) => void
  },
  options: StreamOptions = {}
) {
  const res = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`
    },
    body: JSON.stringify({
      sessionId,
      question,
      regenerate: options.regenerate === true,
      replaceMessageId: options.replaceMessageId || null
    }),
    signal: options.signal
  })
  if (!res.ok || !res.body) {
    let msg = '流式接口失败'
    try {
      const json = await res.json()
      msg = json.message || msg
    } catch {
      /* ignore */
    }
    throw new Error(msg)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finished = false
  const wrapped = {
    onMeta: handlers.onMeta,
    onToken: handlers.onToken,
    onDone: (messageId: number, interrupted?: boolean, suggestions?: string[]) => {
      finished = true
      handlers.onDone(messageId, interrupted, suggestions)
    },
    onError: (message: string) => {
      finished = true
      handlers.onError(message)
    }
  }
  try {
    while (!finished) {
      if (options.signal?.aborted) {
        await reader.cancel()
        break
      }
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''
      for (const part of parts) {
        parseSse(part, wrapped)
        if (finished) break
      }
    }
    if (!finished && buffer.trim()) {
      parseSse(buffer, wrapped)
    }
  } catch (e) {
    if (options.signal?.aborted || (e instanceof DOMException && e.name === 'AbortError')) {
      if (!finished) {
        handlers.onError('已停止生成')
      }
      return
    }
    throw e
  } finally {
    try {
      await reader.cancel()
    } catch {
      /* ignore */
    }
  }
}

function parseSse(
  block: string,
  handlers: {
    onMeta: (sources: SourceItem[], intent?: string, intentLabel?: string) => void
    onToken: (text: string) => void
    onDone: (messageId: number, interrupted?: boolean, suggestions?: string[]) => void
    onError: (message: string) => void
  }
) {
  let event = 'message'
  let data = ''
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) data += line.slice(5).trim()
  }
  if (!data) return
  const payload = JSON.parse(data)
  if (event === 'meta') {
    handlers.onMeta(payload.sources || [], payload.intent || '', payload.intentLabel || '')
  }
  if (event === 'token') handlers.onToken(payload.text || '')
  if (event === 'done') handlers.onDone(payload.messageId, Boolean(payload.interrupted), payload.suggestions || [])
  if (event === 'error') handlers.onError(payload.message || '生成失败')
}

export type SourceItem = { documentName: string; summary: string; score: number }

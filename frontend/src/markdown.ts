/** Minimal safe Markdown → HTML for assistant replies (no external deps). */
export function renderMarkdown(src: string): string {
  if (!src) return ''
  const escaped = escapeHtml(src)
  const parts = escaped.split(/```([\s\S]*?)```/g)
  let html = ''
  for (let i = 0; i < parts.length; i++) {
    if (i % 2 === 1) {
      html += `<pre><code>${parts[i].replace(/^\n+|\n+$/g, '')}</code></pre>`
      continue
    }
    html += formatBlock(parts[i])
  }
  return html
}

function formatBlock(text: string): string {
  const lines = text.split(/\r?\n/)
  const out: string[] = []
  let list: string[] | null = null

  const flushList = () => {
    if (!list) return
    out.push(`<ul>${list.map((item) => `<li>${inline(item)}</li>`).join('')}</ul>`)
    list = null
  }

  for (const raw of lines) {
    const line = raw.trimEnd()
    const trimmed = line.trim()
    if (!trimmed) {
      flushList()
      continue
    }
    const bullet = trimmed.match(/^[-*]\s+(.+)$/)
    if (bullet) {
      if (!list) list = []
      list.push(bullet[1])
      continue
    }
    flushList()
    const heading = trimmed.match(/^(#{1,3})\s+(.+)$/)
    if (heading) {
      const level = heading[1].length
      out.push(`<h${level}>${inline(heading[2])}</h${level}>`)
      continue
    }
    if (/^>\s?/.test(trimmed)) {
      out.push(`<blockquote>${inline(trimmed.replace(/^>\s?/, ''))}</blockquote>`)
      continue
    }
    out.push(`<p>${inline(trimmed)}</p>`)
  }
  flushList()
  return out.join('')
}

function inline(text: string): string {
  return text
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*]+)\*(?!\*)/g, '$1<em>$2</em>')
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

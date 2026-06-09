// SSE client for the Tier 3 ReAct agent stream.
//
// Backend endpoint (POST):  {API_BASE}/chat/agent/stream
// Body: { message, selectedSnippet? }
// Response: text/event-stream
//
// Why fetch + ReadableStream instead of native EventSource:
//   EventSource only supports GET, which puts the whole article in the URL.
//   Tomcat's URL+header size limit (even at 256 KB) can be exceeded by long texts.
//   fetch POST sends the body separately — no URL length constraint at all.
//
// Event lines parsed:
//   event: step  → {type:"step", step:N, content:"..."}
//   event: done  → {type:"done", step:N, content:"..."}
//   event: error → {type:"error", step:N, content:"..."}

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8123/api'

/**
 * Open an agent stream via POST.
 * @param {{message: string, selectedSnippet?: string}} payload
 * @param {{onStep?: Function, onDone?: Function, onError?: Function}} handlers
 * @returns {{ close: () => void }} controller — call close() to abort early.
 */
export function streamAgent(payload, handlers = {}) {
  const controller = new AbortController()
  let settled = false

  const finish = () => {
    settled = true
    controller.abort()
  }

  const parseData = (dataStr) => {
    try {
      return JSON.parse(dataStr)
    } catch {
      return { content: dataStr }
    }
  }

  ;(async () => {
    try {
      const res = await fetch(`${API_BASE}/chat/agent/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: payload.message,
          selectedSnippet: payload.selectedSnippet ?? null,
        }),
        signal: controller.signal,
      })

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`)
      }

      // Read the response as a text stream, splitting on SSE line boundaries.
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = 'message'

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() // last incomplete line stays in buffer

        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            const dataStr = line.slice(5).trim()
            const data = parseData(dataStr)

            if (currentEvent === 'step') {
              if (!settled) handlers.onStep?.(data)
            } else if (currentEvent === 'done') {
              if (!settled) {
                finish()
                handlers.onDone?.(data)
              }
            } else if (currentEvent === 'error') {
              if (!settled) {
                finish()
                handlers.onError?.(data)
              }
            }
            currentEvent = 'message' // reset after each data line
          }
        }
      }

      // Stream ended without a "done" event — treat as completion
      if (!settled) {
        finish()
        handlers.onDone?.({ type: 'done', step: 0, content: '（流结束）' })
      }
    } catch (err) {
      if (settled) return // aborted intentionally — ignore
      finish()
      if (err.name === 'AbortError') return
      handlers.onError?.({
        content:
          err.message?.includes('fetch')
            ? '连接失败：请确认后端服务已启动（http://localhost:8123）。'
            : `请求出错：${err.message}`,
      })
    }
  })()

  return {
    close: () => {
      if (!settled) finish()
    },
  }
}

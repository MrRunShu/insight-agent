// SSE client for the Tier 3 ReAct agent stream.
//
// Backend endpoint (GET):  {API_BASE}/chat/agent/stream?message=...&selectedSnippet=...
// Events:
//   event: step  → {type:"step", step:N, content:"..."}   (think/act progress)
//   event: done  → {type:"done", step:N, content:"..."}   (final answer)
//   event: error → {type:"error", step:N, content:"..."}  (backend error)
//
// Gotcha handled here: a native EventSource auto-reconnects when the server
// closes the stream (which happens normally after the "done" event). We close
// the connection ourselves on done/error and guard with a `settled` flag so a
// trailing connection-close error never re-triggers the run.

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8123/api'

/**
 * Open an agent stream.
 * @param {{message: string, selectedSnippet?: string}} payload
 * @param {{onStep?: Function, onDone?: Function, onError?: Function}} handlers
 * @returns {{ close: () => void }} controller — call close() to abort early.
 */
export function streamAgent(payload, handlers = {}) {
  const params = new URLSearchParams({ message: payload.message })
  if (payload.selectedSnippet) {
    params.append('selectedSnippet', payload.selectedSnippet)
  }

  const url = `${API_BASE}/chat/agent/stream?${params.toString()}`
  const es = new EventSource(url)

  let settled = false
  const finish = () => {
    settled = true
    es.close()
  }

  const parse = (event) => {
    try {
      return JSON.parse(event.data)
    } catch {
      return { content: event.data }
    }
  }

  es.addEventListener('step', (event) => {
    if (settled) return
    handlers.onStep?.(parse(event))
  })

  es.addEventListener('done', (event) => {
    if (settled) return
    const data = parse(event)
    finish()
    handlers.onDone?.(data)
  })

  // Catches both backend "error" events (have .data) and raw connection
  // failures (no .data — e.g. backend down, network drop).
  es.addEventListener('error', (event) => {
    if (settled) return
    finish()
    if (event.data) {
      handlers.onError?.(parse(event))
    } else {
      handlers.onError?.({
        content: '连接中断：请确认后端服务已启动（http://localhost:8123）。',
      })
    }
  })

  return {
    close: () => {
      if (!settled) finish()
    },
  }
}

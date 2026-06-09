# InsightAgent — Frontend

Vue3 + Vite single-page app for the InsightAgent news-analysis assistant.
The **frontend** half of the monorepo; the Spring Boot backend lives in
`../backend`.

```
insight-agent/
├── backend/     ← Spring Boot + Spring AI
├── frontend/    ← you are here (Vue3 + Vite SPA)
├── .vscode/     ← launch config (gitignored)
└── .gitignore   ← shared
```

## Stack

- **Vue 3** (Composition API, `<script setup>`) + **Vite**
- **Naive UI** — light-theme component library (inputs, collapse, buttons, spin)
- **marked** + **highlight.js** — render the agent's Markdown analysis reports
- Native **EventSource** — consumes the backend SSE stream (no extra deps)

## Layout

A single page, two columns:

| Column | Component | Role |
|--------|-----------|------|
| Left   | `ChatPanel.vue`  | Task input + conversation; the final answer renders here as Markdown |
| Right  | `StepsPanel.vue` | Live agent execution — each think/act step as a collapsible card |

`App.vue` owns the state (`messages`, `steps`, `running`) and orchestrates the
stream. `MarkdownView.vue` is the shared Markdown renderer.

## Backend contract

The app talks to a single streaming endpoint:

```
GET {API_BASE}/chat/agent/stream?message=<task>&selectedSnippet=<optional>
```

Server-Sent Events, JSON payload per event:

```
event: step    data: {"type":"step", "step":1, "content":"💭 reasoning or 🔧 Executed: ..."}
event: done    data: {"type":"done", "step":4, "content":"final analysis (Markdown)"}
event: error   data: {"type":"error","step":0, "content":"error message"}
```

`API_BASE` defaults to `http://localhost:8123/api` and can be overridden with a
`VITE_API_BASE` env var (e.g. for production). CORS is enabled backend-side in
`CorsConfig`.

> **SSE gotcha handled in `src/api/agentStream.js`:** a native `EventSource`
> auto-reconnects when the server closes the stream (which happens normally
> after `done`). We `close()` on done/error and guard with a `settled` flag so a
> trailing connection-close never re-triggers the agent run.

## Develop

```powershell
# from this directory
npm install        # first time only
npm run dev        # http://localhost:5173
```

The backend must be running on `:8123` for the stream to connect; otherwise the
UI shows a friendly "连接中断" error.

```powershell
npm run build      # production bundle → dist/
npm run preview    # serve the built bundle
```

## Notes / future tuning

- Naive UI is currently registered globally (`app.use(naive)`), which pulls the
  whole component set into the bundle (~2.3 MB / ~690 KB gzip). For production,
  switch to on-demand imports or `unplugin-vue-components` to shrink it.
- `highlight.js` is imported in full; a language subset would trim more weight.

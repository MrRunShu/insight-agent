# InsightAgent

**个人 AI 助理** — a personal AI assistant for everyday work and study, built on a
hand-rolled ReAct agent (Spring AI + DeepSeek) with RAG over your own knowledge base,
tool use, and real-time step streaming to a Vue 3 frontend.

---

## What it does

Ask it anything. It runs a multi-step **ReAct loop** — reason → call a tool → observe →
repeat — and streams every think/act step to the browser as it happens.

- **Knowledge-base RAG** — drop your own documents (PDFs) into the knowledge base; the agent
  retrieves and **cites** them (source + page) whenever your question touches your own materials.
  Toggle RAG on/off per query to compare grounded vs. ungrounded answers.
- **Tools** — fetch web pages, read/write files — all orchestrated by the agent, not hard-wired.
- **Inline diagrams** — the model can draw flowcharts / structures with **Mermaid**, rendered
  as vector graphics right in the answer.
- **Upload from the UI** — add a document to the knowledge base on the fly (parsed, chunked and
  embedded immediately — no restart).

## Technical highlights

| | |
|---|---|
| **Custom ReAct agent** | 4-layer architecture (`BaseAgent → ReActAgent → ToolCallAgent → InsightAnalyst`), built from scratch — no LangChain / LangGraph |
| **SSE over POST** | `SseEmitter` + `CompletableFuture.runAsync()` on the backend; `fetch()` + `ReadableStream` on the frontend — bypasses `EventSource`'s GET-only limit for large payloads |
| **Spring AI 1.0 GA** | DeepSeek chat + tool calling via the unified `ChatClient` / `ToolCallback` abstraction |
| **RAG** | BGE-M3 embeddings (Ollama, local) + pgvector; robust PDF ingestion via Apache PDFBox |
| **MCP server** | The agent's tools are also exposed over the Model Context Protocol |

## Stack

| Layer | Tech |
|---|---|
| Backend | Java 17 · Spring Boot 3.4 · Spring AI 1.0 |
| LLM | DeepSeek Chat (tool calling) |
| Embeddings | BGE-M3 via Ollama (local inference) |
| Vector store | PostgreSQL + pgvector |
| Frontend | Vue 3 · Vite · Naive UI · Mermaid |

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                    Vue 3 Frontend                     │
│   ChatPanel  ←── SSE stream ──→  StepsPanel           │
│   (Mermaid-rendered answers · 📎 upload)              │
└──────────────────────┬───────────────────────────────┘
                       │ POST /api/chat/agent/stream
┌──────────────────────▼───────────────────────────────┐
│                 Spring Boot Backend                   │
│                                                       │
│  InsightAnalyst  (the assistant agent)                │
│    └─ ToolCallAgent  (tool dispatch + terminate)      │
│         └─ ReActAgent  (think / act loop)             │
│              └─ BaseAgent  (SSE emitter loop)         │
│                                                       │
│  Tools ── searchKnowledgeBase · fetchWebPage · file   │
│  RAG   ── pgvector store · BGE-M3 embeddings          │
└───────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- Java 17+
- PostgreSQL with the `pgvector` extension (`CREATE EXTENSION vector;`)
- Ollama running locally with `bge-m3` pulled (for RAG)
- DeepSeek API key

### Backend

```powershell
setx DEEPSEEK_API_KEY "sk-your-key-here"
setx POSTGRES_PASSWORD "your-db-password"

cd insight-agent/backend
.\mvnw.cmd spring-boot:run
```

Knowledge base: drop PDFs into `insight-agent/backend/papers/` (or set `app.papers.dir`).
They are embedded on first startup, and you can add more later from the UI's **📎 添加论文** button.

### Frontend

```bash
cd insight-agent/frontend
npm install
npm run dev          # http://localhost:5173
```

---

> Personal project. See commit history for how it evolved.

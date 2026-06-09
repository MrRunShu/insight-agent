# InsightAgent

**新闻深度分析助手** — AI-powered news analysis with a custom ReAct agent loop, real-time SSE streaming, and a Vue 3 frontend.

---

## Overview

InsightAgent performs multi-step news analysis using a hand-rolled ReAct agent built on Spring AI and DeepSeek. The agent thinks, calls tools (web scraping, file I/O), observes results, and iterates — streaming each step to the browser in real time via Server-Sent Events.

Built to understand agentic reasoning patterns, Spring AI's tool-calling abstraction, and full-stack SSE streaming end to end.

## Technical Highlights

| | |
|---|---|
| **Custom ReAct agent** | 4-layer architecture (`BaseAgent → ReActAgent → ToolCallAgent → InsightAnalyst`) built from scratch, no LangChain or LangGraph |
| **SSE over POST** | `SseEmitter` + `CompletableFuture.runAsync()` on the backend; `fetch()` + `ReadableStream` on the frontend — bypasses `EventSource`'s GET-only limitation for large article payloads |
| **Spring AI 1.0 GA** | DeepSeek chat + function calling via the unified Spring AI `ChatClient` / `ToolCallback` abstraction |
| **RAG pipeline** | BGE-M3 embeddings (Ollama, local) + PGVector for retrieval-augmented analysis |
| **MCP server** | Analysis tools exposed via the Model Context Protocol |

## Stack

| Layer | Tech |
|---|---|
| Backend | Java 17 · Spring Boot 3.4 · Spring AI 1.0 |
| LLM | DeepSeek Chat (function calling) |
| Embeddings | BGE-M3 via Ollama (local inference) |
| Vector store | PostgreSQL + pgvector |
| Frontend | Vue 3 · Vite · Naive UI |
| API docs | Knife4j / Swagger UI |

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                    Vue 3 Frontend                     │
│   ChatPanel  ←── SSE stream ──→  StepsPanel           │
└──────────────────────┬───────────────────────────────┘
                       │ POST /api/chat/agent/stream
┌──────────────────────▼───────────────────────────────┐
│                 Spring Boot Backend                   │
│                                                       │
│  InsightAnalyst  (domain agent)                       │
│    └─ ToolCallAgent  (tool dispatch + terminate)      │
│         └─ ReActAgent  (think / act loop)             │
│              └─ BaseAgent  (SSE emitter loop)         │
│                                                       │
│  Tools ── WebScrapeTool · TerminateTool · SaveTool    │
│  RAG   ── PGVector store · BGE-M3 embeddings          │
└───────────────────────────────────────────────────────┘
```

## Project Structure

```
insight-agent/
├── backend/                    # Spring Boot + Spring AI
│   └── src/main/java/com/insightagent/
│       ├── agent/              # 4-layer ReAct architecture
│       ├── app/                # InsightApp service facade
│       ├── config/             # CORS, vector store, Swagger
│       ├── controller/         # REST + SSE endpoints
│       └── tools/              # WebScrapeTool, TerminateTool, SaveTool
└── frontend/                   # Vue 3 SPA
    └── src/
        ├── components/         # ChatPanel, StepsPanel, MarkdownView
        └── api/                # SSE client (fetch + ReadableStream)
```

## Quick Start

### Prerequisites

- Java 17+
- PostgreSQL with the `pgvector` extension (`CREATE EXTENSION vector;`)
- Ollama running locally with `bge-m3` pulled (for RAG; optional for basic chat)
- DeepSeek API key

### Backend

```powershell
# Set env vars (one-time; reopen shell after setx)
setx DEEPSEEK_API_KEY "sk-your-key-here"
setx POSTGRES_PASSWORD "your-db-password"

cd insight-agent/backend
.\mvnw.cmd spring-boot:run
```

| URL | Description |
|-----|-------------|
| `http://localhost:8123/api/doc.html` | Knife4j UI (all endpoints) |
| `http://localhost:8123/api/chat/agent/stream` | SSE stream endpoint (POST) |

### Frontend

```bash
cd insight-agent/frontend
npm install
npm run dev          # http://localhost:5173
```

---

> This is an ongoing personal project. See commit history for incremental phases.

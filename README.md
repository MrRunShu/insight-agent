# InsightAgent · 个人 AI 助理

[English](#english) · [中文](#中文)

---

<a name="english"></a>
## English

**InsightAgent** is a personal AI assistant for academic study and everyday tasks. It is built on a hand-rolled ReAct agent (Spring AI + DeepSeek) with RAG over your own paper library, word-by-word streaming output, and a document management UI.

### What it does

Ask anything. The agent runs a multi-step **ReAct loop** — reason → call a tool → observe → repeat — and streams every chunk to the browser as it's generated.

**Academic Growth module**
- Upload PDFs from the UI; they are parsed, chunked, and embedded immediately — no restart needed.
- Ask questions grounded in your paper library; answers cite source and page number.
- Answers support **Mermaid** flowcharts (rendered inline) and **KaTeX** math formulas.
- Manage your library: list, categorize, and soft-delete papers from the UI.

**Assistant chat**
- General-purpose Q&A with optional RAG toggle per query.
- Streaming output — content appears word-by-word as the model generates it.
- Thinking steps visible while the agent is reasoning.

### Technical highlights

| | |
|---|---|
| **Hand-rolled ReAct agent** | 4-layer architecture (`BaseAgent → ReActAgent → ToolCallAgent → InsightAnalyst`), built from scratch — no LangChain |
| **SSE over POST** | `SseEmitter` + `CompletableFuture` on the backend; `fetch()` + `ReadableStream` on the client — bypasses `EventSource`'s GET-only limit for large payloads |
| **Chunk streaming** | Final answer sent as word-boundary `chunk` events (14 ms apart) before a terminal `done` event — enables ChatGPT-style progressive reveal |
| **Spring AI 1.0 GA** | DeepSeek chat + tool calling via `ChatClient` / `ToolCallback` |
| **RAG** | BGE-M3 embeddings (Ollama, local) + pgvector; PDF ingestion via Apache PDFBox; answers include source + page citation |
| **Document metadata** | PostgreSQL `documents` table tracks path, category, upload time, and soft-delete status |
| **Mermaid + KaTeX** | JSON-escaped sequences (`\"`, `\n`) normalized before rendering; Mermaid parse errors are isolated so they don't crash the page |

### Stack

| Layer | Tech |
|---|---|
| Backend | Java 17 · Spring Boot 3.4 · Spring AI 1.0 |
| LLM | DeepSeek Chat (tool calling) |
| Embeddings | BGE-M3 via Ollama (local inference) |
| Vector store | PostgreSQL + pgvector |
| Frontend | Vue 3 · Vite · Marked · Mermaid · KaTeX |

### Architecture

```
┌──────────────────────────────────────────────────────┐
│                    Vue 3 Frontend                    │
│                                                      │
│  WelcomeView  ─── 30% feature cards / 70% chat ───  │
│  AcademicView ─── paper list · upload · Q&A chat ── │
│                                                      │
│  MarkdownView: Mermaid · KaTeX · syntax highlight   │
│  AssistantChat: SSE chunk streaming · step display  │
└──────────────────────┬───────────────────────────────┘
                       │ POST /api/chat/agent/stream
┌──────────────────────▼───────────────────────────────┐
│                 Spring Boot Backend                   │
│                                                       │
│  InsightAnalyst  (the assistant agent)                │
│    └─ ToolCallAgent  (tool dispatch + terminate)      │
│         └─ ReActAgent  (think / act loop)             │
│              └─ BaseAgent  (SSE chunk emitter)        │
│                                                       │
│  Tools ── searchKnowledgeBase · fetchWebPage          │
│  RAG   ── pgvector · BGE-M3 · PDFBox ingestion        │
│  Docs  ── LocalDisk storage · documents metadata DB   │
└───────────────────────────────────────────────────────┘
```

### Quick Start

**Prerequisites:** Java 17+, PostgreSQL with `pgvector`, Ollama with `bge-m3`, DeepSeek API key.

```powershell
# Set environment variables (Windows)
setx DEEPSEEK_API_KEY "sk-your-key"
setx POSTGRES_PASSWORD "your-db-password"

# Backend
cd insight-agent/backend
.\mvnw.cmd spring-boot:run        # → http://localhost:8123/api

# Frontend (separate terminal)
cd insight-agent/frontend
npm install && npm run dev         # → http://localhost:5173
```

---

<a name="中文"></a>
## 中文

**InsightAgent** 是一个面向学术研究和日常任务的个人 AI 助理，基于手写 ReAct 智能体（Spring AI + DeepSeek）构建，支持私人论文知识库 RAG 检索、逐字流式输出和文档管理界面。

### 功能概览

向 Agent 提问，它会执行多步 **ReAct 循环**（思考 → 调用工具 → 观察结果 → 重复），并将每一步的内容实时流式推送到浏览器。

**学术成长模块**
- 通过 UI 上传 PDF，立即解析、分块、向量入库，无需重启服务。
- 基于个人论文库进行问答，答案精确注明来源文件和页码。
- 答案支持 **Mermaid** 流程图（内联渲染）和 **KaTeX** 数学公式。
- 提供论文列表、分类、软删除管理界面。

**通用对话**
- 支持开关 RAG，可逐次对比有无知识库检索的回答差异。
- 流式输出，内容逐字生成，Agent 思考步骤实时可见。

### 技术亮点

| | |
|---|---|
| **手写 ReAct 智能体** | 4层分层架构（`BaseAgent → ReActAgent → ToolCallAgent → InsightAnalyst`），不依赖 LangChain，自主实现 Agent Loop 和死循环检测 |
| **SSE over POST** | 后端 `SseEmitter` + `CompletableFuture` 异步推送；前端 `fetch()` + `ReadableStream` 读取流——绕开 `EventSource` 只支持 GET 的限制，支持任意大小的请求体 |
| **Chunk 流式传输** | 终态答案按词边界切片作为 `chunk` 事件推送（14ms 间隔），实现类 ChatGPT 的逐字显示体验 |
| **Spring AI 1.0 GA** | 通过 `ChatClient` / `ToolCallback` 接入 DeepSeek，支持工具调用 |
| **RAG 知识库** | BGE-M3 向量化（Ollama 本地推理）+ pgvector 存储；Apache PDFBox 解析 PDF；答案注明来源和页码 |
| **文档元数据管理** | PostgreSQL `documents` 表记录路径、分类、上传时间、软删除状态 |
| **Mermaid + KaTeX** | 渲染前还原 JSON 转义字符；Mermaid 渲染失败做异常隔离，不影响页面其余内容 |

### 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.4 · Spring AI 1.0 |
| 大模型 | DeepSeek Chat（支持工具调用） |
| 向量化 | BGE-M3（Ollama 本地推理） |
| 向量数据库 | PostgreSQL + pgvector |
| 前端 | Vue 3 · Vite · Marked · Mermaid · KaTeX |

### 快速启动

**前置条件**：Java 17+、PostgreSQL（需启用 pgvector）、Ollama（已拉取 bge-m3）、DeepSeek API Key。

```powershell
# 设置环境变量（Windows）
setx DEEPSEEK_API_KEY "sk-your-key"
setx POSTGRES_PASSWORD "your-db-password"

# 启动后端
cd insight-agent/backend
.\mvnw.cmd spring-boot:run        # → http://localhost:8123/api

# 启动前端（新终端）
cd insight-agent/frontend
npm install && npm run dev         # → http://localhost:5173
```

---

> Personal project · 个人项目 — See commit history for how it evolved.

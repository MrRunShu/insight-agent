# InsightAgent — Backend

> Personal AI assistant backend — a hand-rolled ReAct agent on Spring AI + DeepSeek,
> with RAG (BGE-M3 + pgvector), tool calling, SSE streaming, and an MCP server.

This is the **backend** half of the monorepo. The Vue3 frontend lives in
`../frontend`. Shared `.gitignore` and `.vscode/` sit one level up in `insight-agent/`.

```
insight-agent/
├── backend/     ← you are here (Spring Boot + Spring AI)
├── frontend/    ← Vue3 + Vite SPA
├── .vscode/     ← launch config (gitignored — holds API key)
└── .gitignore   ← shared
```

## Stack

- Java 17, Spring Boot 3.4
- Spring AI 1.0 (DeepSeek + Ollama starters)
- pgvector (vector store), Apache PDFBox (PDF ingestion)
- Knife4j 4.5 (OpenAPI 3 UI), Hutool 5.8
- Local Maven wrapper (`./mvnw`) — points at `../../.tools/apache-maven-3.9.9`,
  no system Maven install required.

## Quick start

```powershell
# 1. Set DeepSeek key (one-time, reopen shell to take effect)
setx DEEPSEEK_API_KEY "sk-xxxxxxxx"
setx POSTGRES_PASSWORD "your-db-password"

# 2. Start Ollama (for RAG embeddings)
ollama serve
ollama pull bge-m3

# 3. Run app (from this directory)
.\mvnw.cmd spring-boot:run
# or, in VS Code with Java extension: Run the "InsightAgent" launch config
```

Verify:
- Health:     http://localhost:8123/api/actuator/health
- Knife4j UI: http://localhost:8123/api/doc.html

## How it works

A single **ReAct agent** drives every request, built as a 4-layer hierarchy:

```
InsightAnalyst   domain agent — system prompt + tool wiring
  └─ ToolCallAgent   manual tool dispatch + `terminate` completion signal
       └─ ReActAgent   think / act loop
            └─ BaseAgent   step loop + SSE emitter
```

**Endpoint:** `POST /api/chat/agent/stream` — body `{ message, selectedSnippet?, ragEnabled }`,
returns a `text/event-stream` of think/act step events and a final `done` event.

**Tools** (`com.insightagent.tools`):
- `searchKnowledgeBase` — RAG over the user's documents (only active when `ragEnabled`)
- `fetchWebPage` — fetch + clean a web page (direct → Jina Reader fallback)
- `writeFile` / `readFile` — scoped file I/O
- `terminate` — the agent's explicit completion signal

**Knowledge base:** PDFs under `papers/` (or `app.papers.dir`) are parsed with PDFBox,
chunked, embedded with BGE-M3 and stored in pgvector on first startup. Add more at runtime
via `POST /api/papers/upload` (the frontend's 📎 button).

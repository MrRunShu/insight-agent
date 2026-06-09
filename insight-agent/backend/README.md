# InsightAgent — Backend

> Conversational analysis assistant - news-focused, three-tier response.
> Built on Spring AI + DeepSeek + BGE-M3 + PGVector + MCP.

This is the **backend** half of the monorepo. The Vue3 frontend lives in
`../frontend`. Shared `.gitignore` and `.vscode/` sit one level up in
`insight-agent/`.

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
- Knife4j 4.5 (OpenAPI 3 UI), Hutool 5.8
- Local Maven wrapper (`./mvnw`) — points at `../../.tools/apache-maven-3.9.9`,
  no system Maven install required.

## Quick start

```powershell
# 1. Set DeepSeek key (one-time, reopen shell to take effect)
setx DEEPSEEK_API_KEY "sk-xxxxxxxx"

# 2. Start Ollama (optional; needed once RAG turns on)
ollama serve
ollama pull bge-m3

# 3. Run app (from this directory)
.\mvnw.cmd spring-boot:run
# or, in VS Code with Java extension: Run the "InsightAgent" launch config
```

Verify:
- Health:     http://localhost:8123/api/health
- Knife4j UI: http://localhost:8123/api/doc.html
- Swagger:    http://localhost:8123/api/swagger-ui.html

## Architecture

Three-tier response:
1. **Tier 1 - chat** (`/chat`): default, single LLM call.
2. **Tier 2 - per-function** (`/analyze/logic`, `/analyze/verify`, `/analyze/fallacy`, `/analyze/opposite`, `/export/pdf`).
3. **Tier 3 - ReAct deep analysis** (`/analyze/full`): orchestrator + sub-agents.

See planning docs in repo parent directory (gitignored).

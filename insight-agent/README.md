# InsightAgent

> Conversational analysis assistant - news-focused, three-tier response.
> Built on Spring AI + DeepSeek + BGE-M3 + PGVector + MCP.

## Stack (Phase 1+2 skeleton)

- Java 17, Spring Boot 3.4
- Spring AI 1.0 (DeepSeek + Ollama starters)
- Knife4j 4.5 (OpenAPI 3 UI), Hutool 5.8
- Maven (no wrapper yet - use IDE or system `mvn`)

## Quick start

```powershell
# 1. Set DeepSeek key (one-time, reopen shell to take effect)
setx DEEPSEEK_API_KEY "sk-xxxxxxxx"

# 2. Start Ollama (optional for Phase 2; needed once RAG turns on)
ollama serve
ollama pull bge-m3

# 3. Run app (from this directory)
mvn spring-boot:run
# or, in VS Code with Java extension: right-click InsightAgentApplication > Run
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

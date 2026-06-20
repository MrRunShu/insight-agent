package com.insightagent.controller;

import com.insightagent.app.InsightApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat endpoint — the ReAct agent loop, streamed over Server-Sent Events.
 */
@RestController
@RequestMapping("/chat")
@Tag(name = "Chat", description = "ReAct 智能体对话（SSE 流式，支持知识库 RAG）")
public class ChatController {

    @Resource
    private InsightApp insightApp;

    public record ChatRequest(String chatId, String message, String selectedSnippet, Boolean ragEnabled) {}

    /**
     * ReAct agent — streaming SSE variant.
     *
     * <p>Uses POST + request body so long text can be sent without hitting Tomcat's
     * URL/header size limit. The frontend consumes the stream with
     * {@code fetch()} + {@code ReadableStream} (native {@code EventSource} is GET-only).
     *
     * <p>Each agent step is pushed as an SSE event:
     * <pre>
     *   event: step  data: {"type":"step","step":1,"content":"..."}
     *   event: done  data: {"type":"done","step":4,"content":"最终答案..."}
     * </pre>
     */
    @PostMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "ReAct 智能体 — SSE 流式。每个 think/act 步骤实时推送；ragEnabled 开启知识库检索。")
    public SseEmitter agentChatStream(@RequestBody ChatRequest request) {
        boolean rag = Boolean.TRUE.equals(request.ragEnabled());
        return insightApp.doRunAgentStream(request.message(), request.selectedSnippet(), rag);
    }
}

package com.insightagent.controller;

import com.insightagent.app.InsightApp;
import com.insightagent.domain.AnalysisReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Tier 1 chat endpoint (the default path in the three-tier response design).
 * Tier 2 per-function analysis endpoints arrive in Phase 6, Tier 3 ReAct in Phase 8.
 */
@RestController
@RequestMapping("/chat")
@Tag(name = "Chat (Tier 1)", description = "Basic news-analyst chat with conversation memory")
public class ChatController {

    @Resource
    private InsightApp insightApp;

    public record ChatRequest(String chatId, String message, String selectedSnippet) {}
    public record ChatResponse(String chatId, String reply) {}

    @PostMapping
    @Operation(summary = "Send a chat message; reply uses conversation history (Tier 1)")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String chatId = (request.chatId() == null || request.chatId().isBlank())
                ? UUID.randomUUID().toString()
                : request.chatId();
        String reply = insightApp.doChat(chatId, request.message(), request.selectedSnippet());
        return new ChatResponse(chatId, reply);
    }

    @PostMapping("/report")
    @Operation(summary = "Send a chat message and parse the reply into an AnalysisReport JSON")
    public AnalysisReport chatWithReport(@RequestBody ChatRequest request) {
        String chatId = (request.chatId() == null || request.chatId().isBlank())
                ? UUID.randomUUID().toString()
                : request.chatId();
        return insightApp.doChatWithReport(chatId, request.message(), request.selectedSnippet());
    }

    @PostMapping("/rag")
    @Operation(summary = "RAG-augmented chat — reply is grounded in the local knowledge base " +
            "(logical fallacies, media literacy, fact-checking methodology)")
    public ChatResponse ragChat(@RequestBody ChatRequest request) {
        String chatId = (request.chatId() == null || request.chatId().isBlank())
                ? UUID.randomUUID().toString()
                : request.chatId();
        String reply = insightApp.doChatWithRag(chatId, request.message(), request.selectedSnippet());
        return new ChatResponse(chatId, reply);
    }
}

package com.insightagent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct agent backed by a Spring AI {@link ChatClient} with tool calling.
 *
 * <p>Implements the ReAct (Reasoning + Acting) loop:
 * <ol>
 *   <li><b>Think</b>: ask the LLM (with all registered tools) what to do next.
 *       If the model wants to call tools, Spring AI executes them and returns
 *       the results in the same call — this is the "Act + Observe" phase.</li>
 *   <li><b>Finish</b>: if the model produces a final text answer (no tool calls),
 *       the step signals completion.</li>
 * </ol>
 *
 * <p>Conversation history is maintained in-memory across steps so the model
 * can refer back to earlier results.
 */
@Slf4j
public class ReActAgent extends BaseAgent {

    private static final String FINISH_MARKER = "[FINISHED]";

    private final ChatClient chatClient;
    private final ToolCallbackProvider tools;
    private final String systemPrompt;

    /** In-memory message history for this agent run. */
    private final List<Message> history = new ArrayList<>();

    public ReActAgent(ChatClient chatClient,
                      ToolCallbackProvider tools,
                      String systemPrompt,
                      int maxSteps) {
        super(maxSteps);
        this.chatClient = chatClient;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
    }

    // ── BaseAgent hooks ───────────────────────────────────────────────────────

    @Override
    protected void onStart(String request) {
        history.clear();
        history.add(new UserMessage(request));
    }

    /**
     * One ReAct step: send current history to the LLM (with tools), get response.
     *
     * <p>Spring AI handles the tool-execution sub-loop internally: if the model
     * requests tools, the framework executes them and feeds results back before
     * returning. The {@code content()} we receive is always the model's final
     * text for this step.
     */
    @Override
    protected String step() {
        // Build prompt from accumulated history
        String response = chatClient.prompt()
                .system(systemPrompt)
                .messages(history)
                .toolCallbacks(tools)
                .call()
                .content();

        // Add assistant response to history so next step has context
        history.add(new AssistantMessage(response));

        // Detect completion markers — model signals it's done
        if (response.contains(FINISH_MARKER) || looksLikeFinalAnswer(response)) {
            log.info("[ReActAgent] Final answer detected, finishing");
            finish();
            return response.replace(FINISH_MARKER, "").trim();
        }

        // Otherwise add a continuation prompt so the model keeps working
        history.add(new UserMessage(
                "Continue. If you have fully completed the task, end your response with "
                        + FINISH_MARKER));
        return response;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Heuristic: if the response looks like a final structured answer
     * (conclusion, summary, analysis), treat it as complete.
     */
    private boolean looksLikeFinalAnswer(String response) {
        if (response.isBlank()) return false;
        String lower = response.toLowerCase();
        return lower.contains("总结") || lower.contains("结论")
                || lower.contains("综上") || lower.contains("in summary")
                || lower.contains("in conclusion") || lower.contains("to summarize");
    }
}

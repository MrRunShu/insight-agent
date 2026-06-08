package com.insightagent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Concrete ReAct agent that controls tool execution manually.
 *
 * <p>Key difference from a simple "auto-tool" call: Spring AI's internal tool
 * execution is <b>disabled</b> ({@code internalToolExecutionEnabled = false}).
 * This means:
 * <ul>
 *   <li>{@link #think()} gets back the raw LLM response, which may contain tool
 *       call requests — it does <em>not</em> execute them automatically.</li>
 *   <li>{@link #act()} iterates the pending tool calls, executes each one via
 *       {@link ToolCallbackProvider}, and adds a {@link ToolResponseMessage} to
 *       the conversation history.</li>
 * </ul>
 *
 * <p>This explicit split is what makes the think/act phases visible and
 * individually loggable, matching the OpenManus / course architecture.
 *
 * <p>Completion is structural: when the model produces a response with no tool
 * calls, it has finished reasoning — {@link #think()} returns {@code false} and
 * the agent loop terminates naturally. No magic-string markers needed.
 */
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private final ChatClient chatClient;
    private final ToolCallbackProvider tools;
    private final String systemPrompt;

    /** Tool calls from the most recent {@link #think()} call, consumed by {@link #act()}. */
    private List<AssistantMessage.ToolCall> pendingToolCalls;

    public ToolCallAgent(ChatClient chatClient,
                         ToolCallbackProvider tools,
                         String systemPrompt,
                         int maxSteps) {
        super(maxSteps);
        this.chatClient = chatClient;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
    }

    // ── ReActAgent phases ─────────────────────────────────────────────────────

    /**
     * Think: send history to LLM with auto-execution disabled.
     *
     * <p>The model either:
     * <ul>
     *   <li>Requests one or more tool calls → {@code pendingToolCalls} is populated,
     *       return {@code true} so {@link #act()} runs next.</li>
     *   <li>Produces plain text (final answer) → stored in {@link #thinkResult},
     *       return {@code false} so the step finishes.</li>
     * </ul>
     */
    @Override
    protected boolean think() {
        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .messages(history)
                .toolCallbacks(tools)
                .options(DeepSeekChatOptions.builder()
                        .internalToolExecutionEnabled(false)
                        .build())
                .call()
                .chatResponse();

        AssistantMessage assistantMsg = response.getResult().getOutput();
        history.add(assistantMsg);

        pendingToolCalls = assistantMsg.getToolCalls();
        boolean hasToolCalls = pendingToolCalls != null && !pendingToolCalls.isEmpty();

        if (hasToolCalls) {
            log.info("[ToolCallAgent] think → {} tool call(s): {}",
                    pendingToolCalls.size(),
                    pendingToolCalls.stream().map(AssistantMessage.ToolCall::name).toList());
        } else {
            thinkResult = assistantMsg.getText();
            log.info("[ToolCallAgent] think → final answer ({} chars)",
                    thinkResult != null ? thinkResult.length() : 0);
        }
        return hasToolCalls;
    }

    /**
     * Act: execute each pending tool call and add results to history.
     *
     * <p>Tool lookup is by name from the registered {@link ToolCallbackProvider}.
     * If a tool is not found, an error string is returned as the tool result
     * (rather than throwing) so the model can handle the failure gracefully.
     */
    @Override
    protected String act() {
        ToolCallback[] callbacks = tools.getToolCallbacks();
        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

        for (AssistantMessage.ToolCall tc : pendingToolCalls) {
            String args = tc.arguments();
            log.info("[ToolCallAgent] act → {}({})", tc.name(),
                    args.length() > 100 ? args.substring(0, 100) + "…" : args);

            String result = Arrays.stream(callbacks)
                    .filter(cb -> cb.getToolDefinition().name().equals(tc.name()))
                    .findFirst()
                    .map(cb -> {
                        try {
                            return cb.call(tc.arguments());
                        } catch (Exception e) {
                            log.warn("[ToolCallAgent] act → tool error: {}", e.getMessage());
                            return "Tool execution error: " + e.getMessage();
                        }
                    })
                    .orElse("Unknown tool: " + tc.name());

            log.info("[ToolCallAgent] act ← {} ({} chars)", tc.name(), result.length());
            toolResponses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
        }

        history.add(new ToolResponseMessage(toolResponses));

        // Prompt the model to continue reasoning based on the tool results
        history.add(new UserMessage("请根据以上工具执行结果，继续完成任务。"));

        // Return a unique summary for BaseAgent's stuck detection —
        // include tool names + argument hash so different calls produce different strings
        return "Executed: " + pendingToolCalls.stream()
                .map(tc -> tc.name() + "#" + Math.abs(tc.arguments().hashCode()) % 10000)
                .toList();
    }
}

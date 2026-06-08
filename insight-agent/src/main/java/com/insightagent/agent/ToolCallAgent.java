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
import java.util.Optional;

/**
 * Concrete ReAct agent that controls tool execution manually.
 *
 * <p>Spring AI's internal tool execution is <b>disabled</b>
 * ({@code internalToolExecutionEnabled = false}), so every tool call is handled
 * explicitly in {@link #act()} rather than inside the framework's black box.
 *
 * <h3>Completion signal</h3>
 * The agent loop ends only when the model calls the special {@code terminate} tool,
 * passing its final answer as an argument. Plain-text responses <em>without</em> any
 * tool call are treated as mid-reasoning steps, not as task completion — the model
 * receives a nudge to keep going.
 *
 * <p>This avoids the false-positive "done" that would occur if the model produced a
 * plain-text reply early in the reasoning chain before it had finished all its work.
 */
@Slf4j
public class ToolCallAgent extends ReActAgent {

    /** Name of the tool the model calls to explicitly signal task completion. */
    private static final String TERMINATE_TOOL = "terminate";

    private final ChatClient chatClient;
    private final ToolCallbackProvider tools;
    private final String systemPrompt;

    /** Non-terminate tool calls from the most recent {@link #think()}, consumed by {@link #act()}. */
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
     * <p>Three outcomes:
     * <ol>
     *   <li><b>terminate called</b> — model is done. Execute terminate to extract
     *       the final answer, call {@link #finish()}, return {@code false}.</li>
     *   <li><b>other tool(s) called</b> — populate {@link #pendingToolCalls},
     *       return {@code true} so {@link #act()} executes them.</li>
     *   <li><b>no tool calls</b> — model produced plain text mid-reasoning.
     *       Add a nudge to history and return {@code false} without finishing,
     *       so the loop continues.</li>
     * </ol>
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

        List<AssistantMessage.ToolCall> allCalls = assistantMsg.getToolCalls();
        boolean hasAnyCalls = allCalls != null && !allCalls.isEmpty();

        if (!hasAnyCalls) {
            // Model produced plain text without calling any tool — it is reasoning aloud.
            // Nudge it to continue rather than treating this as task completion.
            String text = assistantMsg.getText();
            log.info("[ToolCallAgent] think → no tool calls, model reasoning aloud ({} chars)",
                    text != null ? text.length() : 0);
            history.add(new UserMessage(
                    "请继续完成任务。如果已经全部完成，请调用 terminate 工具提交最终答案。"));
            thinkResult = text;
            return false;   // no act(); loop continues (finish() was NOT called)
        }

        // Check whether the model is calling terminate
        Optional<AssistantMessage.ToolCall> terminateCall = allCalls.stream()
                .filter(tc -> TERMINATE_TOOL.equals(tc.name()))
                .findFirst();

        if (terminateCall.isPresent()) {
            log.info("[ToolCallAgent] think → terminate called, extracting final answer");
            thinkResult = executeTerminate(terminateCall.get());
            finish();       // ← only place finish() is called
            return false;
        }

        // Regular tool calls — hand off to act()
        pendingToolCalls = allCalls;
        log.info("[ToolCallAgent] think → {} tool call(s): {}",
                pendingToolCalls.size(),
                pendingToolCalls.stream().map(AssistantMessage.ToolCall::name).toList());
        return true;
    }

    /**
     * Act: execute each pending (non-terminate) tool call and feed results back to history.
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
        history.add(new UserMessage("请根据以上工具执行结果，继续完成任务。"));

        // Include argument hash so identical tool names with different args aren't treated as stuck
        return "Executed: " + pendingToolCalls.stream()
                .map(tc -> tc.name() + "#" + Math.abs(tc.arguments().hashCode()) % 10000)
                .toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Execute the terminate tool callback to extract the final answer string.
     * Falls back to the raw arguments JSON if the callback cannot be found.
     */
    private String executeTerminate(AssistantMessage.ToolCall tc) {
        return Arrays.stream(tools.getToolCallbacks())
                .filter(cb -> TERMINATE_TOOL.equals(cb.getToolDefinition().name()))
                .findFirst()
                .map(cb -> {
                    try {
                        return cb.call(tc.arguments());
                    } catch (Exception e) {
                        log.warn("[ToolCallAgent] terminate execution error: {}", e.getMessage());
                        return tc.arguments();  // fall back to raw JSON
                    }
                })
                .orElse(tc.arguments());
    }
}

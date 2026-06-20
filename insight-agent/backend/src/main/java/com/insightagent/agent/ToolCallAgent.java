package com.insightagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /**
     * How many times the model may answer in plain text (without calling terminate)
     * before we accept its latest substantial answer as final. Prevents the loop from
     * grinding to {@code maxSteps} when DeepSeek delivers a complete answer as prose
     * instead of calling the terminate tool.
     */
    private static final int MAX_REASONING_NUDGES = 1;

    /** Minimum length for a plain-text response to be treated as a real final answer. */
    private static final int MIN_FINAL_ANSWER_CHARS = 80;

    private final ChatClient chatClient;
    private final ToolCallbackProvider tools;
    /**
     * Completion-signal tools (i.e. {@code terminate}). Kept separate from {@link #tools}
     * so it is sent to the LLM as part of the schema but is NOT shared with the MCP server
     * — terminate is an internal agent control signal, not a capability for external clients.
     */
    private final ToolCallbackProvider completionTools;
    private final String systemPrompt;

    /**
     * Optional RAG tool provider (e.g. searchKnowledgeBase). Null by default; activated
     * via {@link #enableRag(ToolCallbackProvider)} before the agent run starts.
     */
    private ToolCallbackProvider ragTools = null;

    /** Overrides systemPrompt when RAG mode is enabled. Null means use systemPrompt. */
    private String ragSystemPrompt = null;

    /** Non-terminate tool calls from the most recent {@link #think()}, consumed by {@link #act()}. */
    private List<AssistantMessage.ToolCall> pendingToolCalls;

    /** How many plain-text "keep going" nudges have been issued in this run. */
    private int reasoningNudges = 0;

    /** Enable RAG mode — adds the given provider to the tool schema sent to the LLM. */
    public void enableRag(ToolCallbackProvider ragToolProvider) {
        this.ragTools = ragToolProvider;
    }

    /** Replace the system prompt (used by subclasses to switch to a RAG-aware prompt). */
    protected void systemPromptOverride(String newPrompt) {
        // systemPrompt is final; subclasses can call this before run() to swap it.
        // Achieved via a mutable wrapper field — see ragSystemPrompt below.
        this.ragSystemPrompt = newPrompt;
    }

    public ToolCallAgent(ChatClient chatClient,
                         ToolCallbackProvider tools,
                         ToolCallbackProvider completionTools,
                         String systemPrompt,
                         int maxSteps) {
        super(maxSteps);
        this.chatClient = chatClient;
        this.tools = tools;
        this.completionTools = completionTools;
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
        ToolCallbackProvider[] allProviders = ragTools != null
                ? new ToolCallbackProvider[]{tools, completionTools, ragTools}
                : new ToolCallbackProvider[]{tools, completionTools};

        String activePrompt = ragSystemPrompt != null ? ragSystemPrompt : systemPrompt;

        ChatResponse response = chatClient.prompt()
                .system(activePrompt)
                .messages(history)
                .toolCallbacks(allProviders)
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
            // Model produced plain text without calling any tool. This is either
            // mid-reasoning OR a complete answer delivered as prose (DeepSeek often
            // does the latter even though terminate is now a registered tool).
            String text = assistantMsg.getText();
            String trimmed = text == null ? "" : text.strip();

            // Safety net: after a bounded number of "keep going" nudges, accept a
            // substantial plain-text response as the final answer instead of looping
            // to maxSteps waiting for a terminate call that may never come.
            if (trimmed.length() >= MIN_FINAL_ANSWER_CHARS && reasoningNudges >= MAX_REASONING_NUDGES) {
                log.info("[ToolCallAgent] think → accepting plain-text answer as final "
                        + "({} chars, after {} nudge(s))", trimmed.length(), reasoningNudges);
                thinkResult = text;
                finish();
                return false;
            }

            reasoningNudges++;
            log.info("[ToolCallAgent] think → no tool calls ({} chars), nudging to continue ({}/{})",
                    trimmed.length(), reasoningNudges, MAX_REASONING_NUDGES);
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

        // Regular tool calls — emit thinking text before acting, then hand off to act()
        pendingToolCalls = allCalls;
        log.info("[ToolCallAgent] think → {} tool call(s): {}",
                pendingToolCalls.size(),
                pendingToolCalls.stream().map(AssistantMessage.ToolCall::name).toList());

        // Surface the model's reasoning scratchpad as a separate streaming step
        String thinkText = assistantMsg.getText();
        if (thinkText != null && !thinkText.isBlank()) {
            emitIntermediateStep(thinkText.strip());
        }
        return true;
    }

    /**
     * Act: execute each pending (non-terminate) tool call and feed results back to history.
     */
    @Override
    protected String act() {
        // Resolve callbacks from ALL active providers, not just the base tools.
        // RAG-mode tools (e.g. searchKnowledgeBase) live in ragTools — if we only
        // look in `tools` here, those calls resolve to "Unknown tool" at execution
        // time even though they were offered to the model in think().
        List<ToolCallback> allCallbacks = new ArrayList<>(Arrays.asList(tools.getToolCallbacks()));
        if (ragTools != null) {
            allCallbacks.addAll(Arrays.asList(ragTools.getToolCallbacks()));
        }
        ToolCallback[] callbacks = allCallbacks.toArray(new ToolCallback[0]);
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

    private static final ObjectMapper TERMINATE_MAPPER = new ObjectMapper();

    /**
     * Extract the final answer from a terminate tool call.
     *
     * <p>DeepSeek does not reliably use the declared parameter name ({@code finalAnswer}).
     * It sometimes uses {@code arguments}, {@code reason}, {@code answer}, or other names,
     * and sometimes double-wraps the value as a JSON string inside a JSON object.
     *
     * <p>Strategy: parse {@code tc.arguments()} directly, try a priority list of known keys,
     * and recursively unwrap one level of JSON-string nesting if found.
     */
    private String executeTerminate(AssistantMessage.ToolCall tc) {
        String raw = tc.arguments();
        log.info("[ToolCallAgent] executeTerminate raw args: {}",
                raw.length() > 200 ? raw.substring(0, 200) + "…" : raw);
        try {
            JsonNode args = TERMINATE_MAPPER.readTree(raw);
            // Priority list of field names the model might use
            String[] keys = {"finalAnswer", "final_answer", "answer", "reason",
                             "result", "content", "arguments", "text", "response"};
            for (String key : keys) {
                if (args.has(key)) {
                    String value = args.get(key).asText(); // asText() handles both string and non-string nodes
                    log.info("[ToolCallAgent] executeTerminate extracted key='{}' value={}…",
                            key, value.length() > 80 ? value.substring(0, 80) : value);
                    return unwrapIfJson(value);
                }
            }
            // No known key — if there is exactly one field, take its value
            if (args.size() == 1) {
                String value = args.fields().next().getValue().asText();
                return unwrapIfJson(value);
            }
        } catch (Exception e) {
            log.warn("[ToolCallAgent] executeTerminate parse error: {}", e.getMessage());
        }
        return raw; // absolute fallback: return raw args
    }

    /** If {@code s} looks like a JSON object and contains a known answer key, unwrap it. */
    private String unwrapIfJson(String s) {
        if (s == null || !s.trim().startsWith("{")) return s;
        try {
            JsonNode inner = TERMINATE_MAPPER.readTree(s);
            for (String k : new String[]{"answer", "final_answer", "finalAnswer", "reason", "result", "content"}) {
                if (inner.has(k)) return inner.get(k).asText();
            }
        } catch (Exception ignored) { /* not valid JSON — use as-is */ }
        return s;
    }
}

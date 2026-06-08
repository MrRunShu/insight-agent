package com.insightagent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract ReAct agent — defines the Reasoning + Acting pattern on top of {@link BaseAgent}.
 *
 * <p>Splits each loop iteration into two distinct phases:
 * <ol>
 *   <li><b>Think</b>: call the LLM and decide whether tool calls are needed.</li>
 *   <li><b>Act</b>: execute the pending tool calls and feed results back into history.</li>
 * </ol>
 *
 * <p>If {@link #think()} determines that the model produced a final answer (no tool calls),
 * the step finishes immediately — no {@code act()} is called. This makes completion detection
 * structural rather than relying on magic-string heuristics.
 *
 * <p>Conversation history is maintained across steps so each LLM call sees the full
 * reasoning chain.
 *
 * @see ToolCallAgent concrete implementation with Spring AI tool execution
 */
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /** Conversation history maintained across all steps in this agent run. */
    protected final List<Message> history = new ArrayList<>();

    /**
     * Set by {@link #think()} when it determines the model produced a final answer.
     * {@link #step()} returns this value when {@code think()} returns {@code false}.
     */
    protected String thinkResult;

    protected ReActAgent(int maxSteps) {
        super(maxSteps);
    }

    // ── BaseAgent hooks ───────────────────────────────────────────────────────

    @Override
    protected void onStart(String request) {
        history.clear();
        history.add(new UserMessage(request));
    }

    /**
     * One ReAct step: think first, then act only if needed.
     *
     * <p>This method is {@code final} — subclasses implement {@link #think()} and
     * {@link #act()} instead of overriding the step structure.
     */
    @Override
    protected final String step() {
        boolean needsAct = think();
        if (!needsAct) {
            log.info("[ReActAgent] No tool calls — final answer reached");
            finish();
            return thinkResult != null ? thinkResult.trim() : "";
        }
        return act();
    }

    // ── abstract methods ──────────────────────────────────────────────────────

    /**
     * Reasoning phase: send current history to the LLM and decide what to do next.
     *
     * <p>Implementations must:
     * <ul>
     *   <li>Add the {@code AssistantMessage} to {@link #history}.</li>
     *   <li>If no tool calls are pending, set {@link #thinkResult} to the final answer
     *       text and return {@code false}.</li>
     *   <li>If tool calls are pending, store them for {@link #act()} and return
     *       {@code true}.</li>
     * </ul>
     *
     * @return {@code true} if {@link #act()} must be called; {@code false} if done
     */
    protected abstract boolean think();

    /**
     * Acting phase: execute pending tool calls and add results to {@link #history}.
     *
     * <p>Called only when {@link #think()} returned {@code true}.
     *
     * @return a short human-readable summary of what was executed
     *         (used by {@link BaseAgent} for stuck detection)
     */
    protected abstract String act();
}

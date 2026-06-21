package com.insightagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base for all agents — defines the Agent Loop.
 *
 * <p>Uses the <b>Template Method</b> pattern: this class owns the loop logic
 * (step counting, state transitions, stuck detection), while subclasses
 * implement the single-step logic via {@link #step()}.
 *
 * <p>Execution flow:
 * <pre>
 *   run(request)
 *     └─ while RUNNING and steps < maxSteps
 *           └─ step()        ← implemented by subclass
 *           └─ isStuck()?    ← duplicate-output detector
 * </pre>
 */
@Slf4j
public abstract class BaseAgent {

    /** Default maximum steps before the agent is forcibly terminated. */
    public static final int DEFAULT_MAX_STEPS = 10;

    /** How many identical consecutive step outputs trigger "stuck" handling. */
    private static final int STUCK_THRESHOLD = 2;

    /** SSE timeout: 5 minutes — enough for a long agent run. */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Getter
    private AgentState state = AgentState.IDLE;

    /**
     * Set during {@link #runStream} so subclasses can push thinking text as a
     * separate SSE step event before the tool-execution result.
     * Null in synchronous {@link #run} mode — callers must null-check.
     */
    private java.util.function.Consumer<String> intermediateStepEmitter = null;

    /**
     * Emit a mid-step thinking event (no-op in synchronous {@link #run} mode).
     * Call from {@code think()} to surface the model's reasoning to the frontend.
     */
    protected void emitIntermediateStep(String content) {
        if (intermediateStepEmitter != null && content != null && !content.isBlank()) {
            intermediateStepEmitter.accept(content);
        }
    }

    @Getter
    private int currentStep = 0;

    private final int maxSteps;

    /** Step results collected during this run (used for stuck detection). */
    private final List<String> stepResults = new ArrayList<>();

    protected BaseAgent() {
        this(DEFAULT_MAX_STEPS);
    }

    protected BaseAgent(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    // ── public entry point ────────────────────────────────────────────────────

    /**
     * Execute the agent loop for the given request.
     *
     * @param request user's initial message or task description
     * @return concatenated results of all steps
     */
    public final String run(String request) {
        if (state != AgentState.IDLE) {
            throw new IllegalStateException(
                    "Cannot start agent from state " + state + "; create a new instance.");
        }

        log.info("[{}] Starting — max {} steps", getClass().getSimpleName(), maxSteps);
        setState(AgentState.RUNNING);
        stepResults.clear();
        currentStep = 0;

        try {
            // Pass the initial request to subclass before the loop
            onStart(request);

            while (state == AgentState.RUNNING && currentStep < maxSteps) {
                currentStep++;
                log.info("[{}] Step {}/{}", getClass().getSimpleName(), currentStep, maxSteps);

                String result = step();
                stepResults.add(result);
                log.debug("[{}] Step {} result: {}", getClass().getSimpleName(), currentStep,
                        result.length() > 120 ? result.substring(0, 120) + "…" : result);

                if (isStuck()) {
                    log.warn("[{}] Stuck detected at step {}", getClass().getSimpleName(), currentStep);
                    handleStuckState();
                }
            }

            if (currentStep >= maxSteps && state == AgentState.RUNNING) {
                log.warn("[{}] Reached max steps ({}), terminating", getClass().getSimpleName(), maxSteps);
                stepResults.add("⚠️ Agent terminated: reached max steps (" + maxSteps + ").");
                setState(AgentState.FINISHED);
            }

        } catch (Exception e) {
            log.error("[{}] Error in step {}: {}", getClass().getSimpleName(), currentStep, e.getMessage(), e);
            setState(AgentState.ERROR);
            return "Agent error at step " + currentStep + ": " + e.getMessage();
        }

        String finalResult = String.join("\n\n", stepResults);
        log.info("[{}] Finished in {} steps", getClass().getSimpleName(), currentStep);
        return finalResult.isBlank() ? "No steps executed." : finalResult;
    }

    /**
     * Streaming variant of {@link #run}: runs the agent loop asynchronously and pushes
     * each step result as a Server-Sent Event.
     *
     * <p>Event format (JSON, sent as the SSE {@code data} field):
     * <pre>
     *   {"type":"step",  "step":N, "content":"step result summary"}
     *   {"type":"done",  "step":N, "content":"final answer text"}
     *   {"type":"error", "step":N, "content":"error message"}
     * </pre>
     *
     * <p>The SSE event {@code name} mirrors the {@code type} field, so clients can
     * filter with {@code eventSource.addEventListener("done", ...)}.
     *
     * @param request user's task description
     * @return {@link SseEmitter} — caller must return this directly from the controller
     */
    public final SseEmitter runStream(String request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        if (state != AgentState.IDLE) {
            sendSseEvent(emitter, "error", 0, "Agent is not in IDLE state; create a new instance.");
            emitter.complete();
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            log.info("[{}] Starting stream — max {} steps", getClass().getSimpleName(), maxSteps);
            setState(AgentState.RUNNING);
            stepResults.clear();
            currentStep = 0;
            // Wire up intermediate-step emitter so think() can push reasoning text
            intermediateStepEmitter = text -> sendSseEvent(emitter, "step", currentStep, text);

            try {
                onStart(request);

                while (state == AgentState.RUNNING && currentStep < maxSteps) {
                    currentStep++;
                    log.info("[{}] Stream step {}/{}", getClass().getSimpleName(), currentStep, maxSteps);

                    String result = step();
                    stepResults.add(result);
                    log.debug("[{}] Stream step {} result: {}", getClass().getSimpleName(), currentStep,
                            result.length() > 120 ? result.substring(0, 120) + "…" : result);

                    // If finish() was called inside step(), stream the final answer in chunks.
                    if (state == AgentState.FINISHED) {
                        streamDone(emitter, currentStep, result);
                    } else {
                        sendSseEvent(emitter, "step", currentStep, result);
                    }

                    if (isStuck()) {
                        log.warn("[{}] Stuck at step {}", getClass().getSimpleName(), currentStep);
                        handleStuckState();
                        sendSseEvent(emitter, "done", currentStep,
                                "⚠️ Agent stuck: repeating the same output. Stopping.");
                    }
                }

                if (currentStep >= maxSteps && state == AgentState.RUNNING) {
                    log.warn("[{}] Reached max steps ({}), terminating", getClass().getSimpleName(), maxSteps);
                    setState(AgentState.FINISHED);
                    sendSseEvent(emitter, "done", currentStep,
                            "⚠️ Agent terminated: reached max steps (" + maxSteps + ").");
                }

                emitter.complete();

            } catch (Exception e) {
                log.error("[{}] Stream error at step {}: {}", getClass().getSimpleName(), currentStep,
                        e.getMessage(), e);
                setState(AgentState.ERROR);
                sendSseEvent(emitter, "error", currentStep, "Agent error: " + e.getMessage());
                emitter.complete();
            }
        });

        return emitter;
    }

    // ── abstract / hook methods ───────────────────────────────────────────────

    /**
     * Execute a single step. Subclasses implement the actual think/act logic here.
     * May call {@link #setState(AgentState)} to transition to {@code FINISHED}.
     *
     * @return a human-readable summary of what happened in this step
     */
    protected abstract String step();

    /**
     * Hook called once before the loop starts, with the original user request.
     * Subclasses can use this to prime the conversation memory.
     */
    protected void onStart(String request) {
        // default: no-op
    }

    // ── state helpers ─────────────────────────────────────────────────────────

    protected void setState(AgentState newState) {
        log.debug("[{}] State: {} → {}", getClass().getSimpleName(), state, newState);
        this.state = newState;
    }

    protected void finish() {
        setState(AgentState.FINISHED);
    }

    // ── stuck detection ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the last {@value #STUCK_THRESHOLD} step results are identical,
     * indicating the agent is looping without making progress.
     */
    private boolean isStuck() {
        int size = stepResults.size();
        if (size < STUCK_THRESHOLD) return false;
        String last = stepResults.get(size - 1);
        for (int i = size - 2; i >= size - STUCK_THRESHOLD; i--) {
            if (!last.equals(stepResults.get(i))) return false;
        }
        return true;
    }

    /** Default stuck-state handler: terminate the agent. */
    protected void handleStuckState() {
        log.warn("[{}] Handling stuck state — forcing finish", getClass().getSimpleName());
        stepResults.add("⚠️ Agent stuck: repeating the same output. Stopping.");
        setState(AgentState.FINISHED);
    }

    // ── SSE helpers ───────────────────────────────────────────────────────────

    /**
     * Stream the final answer as incremental "chunk" events (word-boundary chunks),
     * then send the full content as the terminal "done" event for clients that joined late.
     */
    private void streamDone(SseEmitter emitter, int step, String content) {
        int len = content.length();
        int pos = 0;
        while (pos < len) {
            // Advance to the next whitespace boundary, targeting ~30 chars per chunk.
            int end = Math.min(pos + 30, len);
            while (end < len && end < pos + 60 && !Character.isWhitespace(content.charAt(end))) {
                end++;
            }
            end = Math.min(end, len);
            sendSseEvent(emitter, "chunk", step, content.substring(0, end));
            pos = end;
            if (pos < len) {
                try { Thread.sleep(14); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
        sendSseEvent(emitter, "done", step, content);
    }

    /**
     * Serialize and send one SSE event. Swallows IO errors — a broken connection
     * should not propagate into the agent loop as an unhandled exception.
     */
    private void sendSseEvent(SseEmitter emitter, String type, int step, String content) {
        try {
            String json = MAPPER.writeValueAsString(new AgentStreamEvent(type, step, content));
            emitter.send(SseEmitter.event().name(type).data(json));
        } catch (Exception e) {
            log.warn("[{}] Failed to send SSE event (type={}): {}", getClass().getSimpleName(), type,
                    e.getMessage());
        }
    }

    /** Payload for each streamed agent event. */
    public record AgentStreamEvent(String type, int step, String content) {}
}

package com.insightagent.agent;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

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

    @Getter
    private AgentState state = AgentState.IDLE;

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
}

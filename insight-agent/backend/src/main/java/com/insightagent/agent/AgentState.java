package com.insightagent.agent;

/**
 * Life-cycle states for an agent execution run.
 *
 * <p>State transitions:
 * <pre>
 *   IDLE → RUNNING → FINISHED
 *              ↓
 *            ERROR
 * </pre>
 */
public enum AgentState {
    /** Waiting to be started. */
    IDLE,
    /** Actively executing steps. */
    RUNNING,
    /** Completed all steps or reached a terminal condition. */
    FINISHED,
    /** Aborted due to an unrecoverable error. */
    ERROR
}

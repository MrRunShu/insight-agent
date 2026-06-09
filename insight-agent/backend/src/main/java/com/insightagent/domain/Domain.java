package com.insightagent.domain;

/**
 * Top-level subject area used to scope analysis and pick the right RAG bucket.
 * See project design §5.2 (domain knowledge bases).
 */
public enum Domain {
    ECONOMY,
    POLITICS,
    TECH,
    SCIENCE,
    HEALTH,
    INTERNATIONAL,
    GENERAL
}

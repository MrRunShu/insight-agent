package com.insightagent.domain;

/**
 * Four-axis scoring from {@code ScorerAgent} (Phase 8). Each axis 0-10.
 * {@code overall} is a weighted average exposed as a convenience.
 */
public record Score(
        int factualAccuracy,
        int sourceReliability,
        int logicalRigor,
        int balance,
        double overall
) {}

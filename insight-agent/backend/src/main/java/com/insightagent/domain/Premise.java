package com.insightagent.domain;

/**
 * One premise supporting the article's main claim.
 *
 * @param statement  the premise itself
 * @param evidence   raw quote / data backing it (nullable)
 * @param strength   subjective 0-10 score from the analyst
 */
public record Premise(String statement, String evidence, int strength) {}

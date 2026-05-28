package com.insightagent.domain;

import java.util.List;

/**
 * Decomposed logical structure of an article — Toulmin-style.
 *
 * @param mainClaim         the article's main assertion
 * @param premises          supporting premises (with evidence + strength)
 * @param hiddenAssumptions assumptions the article relies on but doesn't state
 * @param reasoning         reasoning mode
 * @param detectedFallacies fallacies found in the argument
 */
public record LogicChain(
        String mainClaim,
        List<Premise> premises,
        List<String> hiddenAssumptions,
        ReasoningType reasoning,
        List<Fallacy> detectedFallacies
) {}

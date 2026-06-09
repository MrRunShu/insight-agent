package com.insightagent.domain;

import java.util.List;

/**
 * Single fact-check entry. The verdict comes from the FactCheckerAgent (Phase 6+).
 *
 * @param claim      the asserted fact under check
 * @param verdict    SUPPORTED / DISPUTED / FALSE / UNVERIFIABLE
 * @param confidence 0.0 - 1.0
 * @param sources    URLs / citations backing the verdict
 */
public record FactCheck(String claim, String verdict, double confidence, List<String> sources) {}

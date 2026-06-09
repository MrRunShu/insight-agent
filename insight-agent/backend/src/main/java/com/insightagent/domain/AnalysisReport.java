package com.insightagent.domain;

import java.util.List;

/**
 * Full analysis report — the deliverable of Tier 3 (ReAct deep analysis).
 * Tier 2 endpoints (single-function analysis) return sub-records of this.
 * Defined now in Phase 3 per project guidance §5.
 *
 * @param title               article title (extracted)
 * @param summary             2-4 sentence summary
 * @param domain              subject area
 * @param entities            named entities found
 * @param clickbaitScore      0-10 (0 = neutral title, 10 = pure clickbait)
 * @param logic               logical decomposition
 * @param factChecks          per-claim fact-check entries
 * @param oppositeViewpoints  steelmanned counter-arguments
 * @param score               4-axis score
 * @param conclusion          analyst's overall take
 * @param mermaidLogicDiagram Mermaid flowchart source code (rendered on frontend)
 */
public record AnalysisReport(
        String title,
        String summary,
        Domain domain,
        List<Entity> entities,
        int clickbaitScore,
        LogicChain logic,
        List<FactCheck> factChecks,
        List<String> oppositeViewpoints,
        Score score,
        String conclusion,
        String mermaidLogicDiagram
) {}

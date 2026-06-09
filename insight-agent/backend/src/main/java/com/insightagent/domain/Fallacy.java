package com.insightagent.domain;

/**
 * Logical fallacy detected in the article.
 *
 * @param type      canonical name (e.g. "Ad Hominem", "Strawman", "Post Hoc")
 * @param quote     verbatim excerpt where the fallacy appears
 * @param explanation analyst's note on why this counts as that fallacy
 */
public record Fallacy(String type, String quote, String explanation) {}

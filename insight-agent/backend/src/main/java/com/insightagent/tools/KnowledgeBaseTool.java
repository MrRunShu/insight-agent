package com.insightagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG tool — gives the agent on-demand access to the methodology knowledge base
 * (logical fallacies, Toulmin argumentation model, media literacy, fact-checking).
 *
 * <p>Intentionally NOT registered in {@code insightToolCallbackProvider} so the MCP
 * server never exposes it to external clients. Registered separately only when
 * RAG mode is enabled for an agent run.
 */
@Component
@Slf4j
public class KnowledgeBaseTool {

    private static final double SIMILARITY_THRESHOLD = 0.45;
    private static final int TOP_K = 3;

    @Autowired
    private VectorStore insightVectorStore;

    @Tool(description = """
            Search the methodology knowledge base for definitions of logical fallacies,
            argumentation frameworks (e.g. Toulmin model), media literacy principles,
            and fact-checking methodology.
            Call this when you need to:
            - Identify and name a specific logical fallacy in the text
            - Apply a structured argumentation framework
            - Cite source-credibility evaluation criteria
            Do NOT call this for current events or factual lookups — use fetchWebPage for those.
            """)
    public String searchKnowledgeBase(
            @ToolParam(description = "Search query in Chinese or English, e.g. '滑坡谬误', 'Toulmin model', 'source credibility CRAAP'")
            String query) {

        log.info("[KnowledgeBaseTool] searching: {}", query);
        List<Document> docs = insightVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(TOP_K)
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .build());

        if (docs.isEmpty()) {
            log.info("[KnowledgeBaseTool] no results above threshold for: {}", query);
            return "知识库中未找到与该查询相关的内容（相似度低于阈值）。";
        }

        String result = docs.stream()
                .map(Document::getFormattedContent)
                .collect(Collectors.joining("\n\n---\n\n"));

        log.info("[KnowledgeBaseTool] found {} chunks ({} chars)", docs.size(), result.length());
        return result;
    }
}

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
 * RAG tool — gives the agent on-demand access to the personal academic paper
 * knowledge base (the user's collected research papers, embedded into pgvector).
 *
 * <p>Intentionally NOT registered in {@code insightToolCallbackProvider} so the MCP
 * server never exposes it to external clients. Registered separately only when
 * RAG mode is enabled for an agent run.
 */
@Component
@Slf4j
public class KnowledgeBaseTool {

    private static final double SIMILARITY_THRESHOLD = 0.35;
    private static final int TOP_K = 5;

    @Autowired
    private VectorStore insightVectorStore;

    @Tool(description = """
            Search the user's personal knowledge base — their own documents (papers,
            notes, references). Returns the most relevant passages with their source
            filename so answers can cite where each claim comes from.
            Call this whenever the question touches the user's own materials, e.g.:
            - Answer a question about something in the user's documents
            - Explain a concept, method, or result described in their materials
            - Find and relate ideas across multiple documents (cross-document synthesis)
            Use a focused query phrasing the concept or question; one topic per call.
            Skip it for general-knowledge questions the model already knows.
            """)
    public String searchKnowledgeBase(
            @ToolParam(description = "Search query in Chinese or English, e.g. 'chain-of-thought prompting', '工具调用 agent 框架', 'retrieval augmented generation'")
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
            return "知识库中未找到与该查询相关的论文内容（相似度低于阈值）。";
        }

        String result = docs.stream()
                .map(doc -> {
                    String source = doc.getMetadata().getOrDefault("source", "论文").toString();
                    Object page = doc.getMetadata().get("page_number");
                    String cite = "【来源：" + source + (page != null ? "，第 " + page + " 页" : "") + "】";
                    // Use getText() (raw content) rather than getFormattedContent(), which
                    // prepends noisy metadata (source/distance/file_name) to every chunk.
                    return cite + "\n" + doc.getText();
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        log.info("[KnowledgeBaseTool] found {} chunk(s) ({} chars)", docs.size(), result.length());
        return result;
    }
}

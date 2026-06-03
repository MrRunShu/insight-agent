package com.insightagent.app;

import com.insightagent.advisor.LoggerAdvisor;
import com.insightagent.chatmemory.FileChatMemoryRepository;
import com.insightagent.domain.AnalysisReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Tier 1 entry point — basic chat for news analysis. Default response path in the three-tier
 * design: low-cost single LLM call with conversation memory.
 *
 * <p>Tier 2 (per-function endpoints) and Tier 3 (ReAct deep analysis) live in their own
 * services in later phases; both will share {@link FileChatMemoryRepository} for continuity.
 */
@Component
@Slf4j
public class InsightApp {

    /** Memory window size — how many recent messages flow back into each prompt. */
    private static final int MEMORY_WINDOW = 20;

    /**
     * Minimum cosine similarity for a retrieved chunk to be included in the RAG context.
     * Chunks below this threshold are filtered out to reduce noise.
     */
    private static final double RAG_SIMILARITY_THRESHOLD = 0.50;

    /** Number of top-k chunks to retrieve per query. */
    private static final int RAG_TOP_K = 4;

    private final ChatClient chatClient;

    /** Injected after construction — avoids circular dependency with VectorStoreConfig. */
    @Autowired
    private VectorStore insightVectorStore;

    public InsightApp(ChatModel deepSeekChatModel,
                      FileChatMemoryRepository memoryRepository,
                      @Value("classpath:prompt/insight-analyst-system.st") Resource systemPromptResource) {

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(memoryRepository)
                .maxMessages(MEMORY_WINDOW)
                .build();

        this.chatClient = ChatClient.builder(deepSeekChatModel)
                .defaultSystem(systemPromptResource)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new LoggerAdvisor()
                        // SafeGuardAdvisor intentionally NOT added — news analysis often
                        // needs to discuss sensitive topics; safety is steered via system prompt.
                        // ReReadingAdvisor intentionally NOT added — doubles token cost,
                        // reserved for Tier 3 deep analyst sub-agents.
                )
                .build();
    }

    /**
     * Tier 1 free-form chat with conversation memory.
     *
     * @param chatId          stable conversation id (UUID per session is fine)
     * @param message         user message
     * @param selectedSnippet optional news snippet the user has highlighted; folded into
     *                        the prompt as analysis context. Null/blank for plain chat.
     * @return assistant reply
     */
    public String doChat(String chatId, String message, String selectedSnippet) {
        String userText = buildUserText(message, selectedSnippet);

        return chatClient.prompt()
                .user(userText)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }

    /**
     * Tier 1 chat that returns a {@link AnalysisReport}-shaped response via Spring AI's
     * structured-output entity mapping.
     */
    public AnalysisReport doChatWithReport(String chatId, String message, String selectedSnippet) {
        String userText = buildUserText(message, selectedSnippet);

        return chatClient.prompt()
                .system(sp -> sp.text("""
                        Produce an AnalysisReport JSON. Use null for fields you cannot fill
                        from the current message — do NOT invent data. mermaidLogicDiagram
                        may be omitted for short messages.
                        """))
                .user(userText)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(AnalysisReport.class);
    }

    /**
     * Tier 1 chat augmented with the knowledge base (RAG — Phase 5 pipeline).
     *
     * <p>Uses {@link RetrievalAugmentationAdvisor} with:
     * <ul>
     *   <li>{@link VectorStoreDocumentRetriever} — retrieves top-{@value #RAG_TOP_K} chunks
     *       from PGVector, filtered by cosine similarity ≥ {@value #RAG_SIMILARITY_THRESHOLD}.</li>
     *   <li>{@link ContextualQueryAugmenter} — rewrites follow-up questions using conversation
     *       history before the vector search, so "what about the other point?" resolves correctly.
     *       {@code allowEmptyContext=true} means the model still answers even if no chunks pass
     *       the similarity threshold.</li>
     * </ul>
     *
     * @param chatId          stable conversation id
     * @param message         user message
     * @param selectedSnippet optional highlighted news snippet
     * @return assistant reply grounded in the knowledge base
     */
    public String doChatWithRag(String chatId, String message, String selectedSnippet) {
        String userText = buildUserText(message, selectedSnippet);

        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(insightVectorStore)
                        .similarityThreshold(RAG_SIMILARITY_THRESHOLD)
                        .topK(RAG_TOP_K)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();

        return chatClient.prompt()
                .user(userText)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(ragAdvisor)
                .call()
                .content();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String buildUserText(String message, String selectedSnippet) {
        return (selectedSnippet == null || selectedSnippet.isBlank())
                ? message
                : message + "\n\n[USER-SELECTED SNIPPET]\n" + selectedSnippet;
    }
}

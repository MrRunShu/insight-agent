package com.insightagent.app;

import com.insightagent.advisor.LoggerAdvisor;
import com.insightagent.agent.InsightAnalyst;
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
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Tier 1 entry point — basic chat for news analysis. Default response path in the three-tier
 * design: low-cost single LLM call with conversation memory.
 *
 * <p>Tier 3 (ReAct agent loop) is exposed via {@link #doRunAgent} — same bean, different method.
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

    /** Tool registry shared with MCP server — avoids duplicating tool instances. */
    @Autowired
    private ToolCallbackProvider insightToolCallbackProvider;

    /** Prototype provider — each call to getObject() returns a fresh InsightAnalyst instance. */
    @Autowired
    private ObjectProvider<InsightAnalyst> yuManusProvider;

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

    /**
     * Tier 1 chat with tool calling enabled (Phase 6).
     *
     * <p>Registered tools:
     * <ul>
     *   <li>{@link WebScrapeTool#fetchWebPage} — let the model fetch a news URL on demand</li>
     *   <li>{@link FileOperationTool#writeFile} / {@link FileOperationTool#readFile}
     *       — persist analysis reports to {@code ./tmp/insight/}</li>
     * </ul>
     *
     * <p>Typical usage: user sends a URL in the message; the model calls {@code fetchWebPage},
     * receives the article text, then produces a structured analysis — all in one turn.
     *
     * @param chatId          stable conversation id
     * @param message         user message (may contain a news URL)
     * @param selectedSnippet optional pre-selected text; if blank the model decides whether
     *                        to fetch a URL from the message
     * @return assistant reply, potentially after one or more tool-call round-trips
     */
    public String doChatWithTools(String chatId, String message, String selectedSnippet) {
        String userText = buildUserText(message, selectedSnippet);

        return chatClient.prompt()
                .user(userText)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .tools(insightToolCallbackProvider)
                .call()
                .content();
    }

    /**
     * Tier 3 ReAct agent loop (Phase 8).
     *
     * <p>Obtains a fresh {@link InsightAnalyst} instance per call (prototype-scoped Spring bean)
     * so each request starts with a clean conversation history. The agent runs the
     * think → act loop autonomously until the model produces a final answer without
     * requesting any further tool calls, or the step limit is reached.
     *
     * <p>Unlike the Tier 1 methods there is no persistent {@code chatId} — the agent's
     * own multi-step history already acts as its context.
     *
     * @param message         user message or task description
     * @param selectedSnippet optional news snippet; folded into the first user message
     * @return final result from the agent run
     */
    public String doRunAgent(String message, String selectedSnippet) {
        InsightAnalyst agent = yuManusProvider.getObject();
        return agent.run(buildUserText(message, selectedSnippet));
    }

    /**
     * Tier 3 ReAct agent — streaming variant (Phase 9).
     *
     * <p>Pushes each agent step as an SSE event so the frontend can render the
     * reasoning chain in real time. Event schema: see {@link BaseAgent#runStream}.
     *
     * @param message         user task description
     * @param selectedSnippet optional highlighted news snippet
     * @return {@link SseEmitter} that streams step events and a final "done" event
     */
    public SseEmitter doRunAgentStream(String message, String selectedSnippet, boolean ragEnabled) {
        InsightAnalyst agent = yuManusProvider.getObject();
        if (ragEnabled) {
            agent.enableRag();
            log.info("[InsightApp] RAG mode enabled for this agent run");
        }
        return agent.runStream(buildUserText(message, selectedSnippet));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String buildUserText(String message, String selectedSnippet) {
        return (selectedSnippet == null || selectedSnippet.isBlank())
                ? message
                : message + "\n\n[USER-SELECTED SNIPPET]\n" + selectedSnippet;
    }
}

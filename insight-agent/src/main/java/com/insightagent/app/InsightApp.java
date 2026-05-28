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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Tier 1 entry point — basic chat for news analysis. Default response path in the three-tier
 * design (see project design §3): low-cost single LLM call with conversation memory.
 *
 * <p>Tier 2 (per-function endpoints) and Tier 3 (ReAct deep analysis) live in their own
 * services in later phases; both will share {@link FileChatMemoryRepository} for continuity.
 */
@Component
@Slf4j
public class InsightApp {

    /** Memory window size — how many recent messages flow back into each prompt. */
    private static final int MEMORY_WINDOW = 20;

    private final ChatClient chatClient;

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
                        // needs to discuss sensitive topics; safety is steered via system
                        // prompt ("analyse logic, don't take sides"). See project design §13.
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
        String userText = (selectedSnippet == null || selectedSnippet.isBlank())
                ? message
                : message + "\n\n[USER-SELECTED SNIPPET]\n" + selectedSnippet;

        return chatClient.prompt()
                .user(userText)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
    }

    /**
     * Tier 1 chat that returns a {@link AnalysisReport}-shaped response via Spring AI's
     * structured-output entity mapping. Tier 3 will fill in all fields properly through
     * the orchestrator; here we just demonstrate the pattern so the POJOs are exercised.
     */
    public AnalysisReport doChatWithReport(String chatId, String message, String selectedSnippet) {
        String userText = (selectedSnippet == null || selectedSnippet.isBlank())
                ? message
                : message + "\n\n[USER-SELECTED SNIPPET]\n" + selectedSnippet;

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
}

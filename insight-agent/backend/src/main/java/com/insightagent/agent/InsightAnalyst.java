package com.insightagent.agent;

import com.insightagent.tools.KnowledgeBaseTool;
import com.insightagent.tools.TerminateTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Domain-specific agent for news analysis — the top of the 4-layer hierarchy.
 *
 * <p>Available tools (always): fetchWebPage, writeFile, readFile, terminate.
 * <p>Optional tool (RAG mode): searchKnowledgeBase — activated via {@link #enableRag()}.
 *
 * <p>Prototype-scoped so each request gets a fresh instance with clean state.
 * Inject via {@code ObjectProvider<InsightAnalyst>}.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InsightAnalyst extends ToolCallAgent {

    private static final String SYSTEM_PROMPT_BASE = """
            你是 InsightAgent（分析大师），一个多步骤新闻分析助手。
            可用工具：
              - fetchWebPage：抓取指定 URL 的网页内容
              - writeFile：将分析报告保存到本地文件
              - readFile：读取已保存的本地文件
              - terminate：任务全部完成后调用此工具，将最终答案作为参数传入
            工作流程：先获取内容，再深入分析，最后调用 terminate 提交最终答案。
            只有在确认任务彻底完成时才调用 terminate，中间步骤不要调用。
            """;

    private static final String SYSTEM_PROMPT_RAG = """
            你是 InsightAgent（分析大师），一个多步骤新闻分析助手。
            可用工具：
              - fetchWebPage：抓取指定 URL 的网页内容
              - writeFile：将分析报告保存到本地文件
              - readFile：读取已保存的本地文件
              - searchKnowledgeBase：检索逻辑学方法论知识库（逻辑谬误定义、Toulmin 论证模型、媒体素养、事实核查方法）
              - terminate：任务全部完成后调用此工具，将最终答案作为参数传入
            工作流程：
              1. 获取新闻内容（fetchWebPage 或直接读取用户输入）
              2. 调用 searchKnowledgeBase 检索相关分析框架和谬误定义，用专业术语锚定分析
              3. 深入分析，引用知识库中的方法论支撑结论
              4. 调用 terminate 提交最终答案
            只有在确认任务彻底完成时才调用 terminate，中间步骤不要调用。
            """;

    private final KnowledgeBaseTool knowledgeBaseTool;

    public InsightAnalyst(@Qualifier("deepSeekChatModel") ChatModel chatModel,
                          ToolCallbackProvider insightToolCallbackProvider,
                          TerminateTool terminateTool,
                          KnowledgeBaseTool knowledgeBaseTool) {
        super(ChatClient.builder(chatModel).build(),
                insightToolCallbackProvider,
                MethodToolCallbackProvider.builder().toolObjects(terminateTool).build(),
                SYSTEM_PROMPT_BASE, BaseAgent.DEFAULT_MAX_STEPS);
        this.knowledgeBaseTool = knowledgeBaseTool;
    }

    /**
     * Switch to RAG mode: adds searchKnowledgeBase to the tool schema and updates
     * the system prompt to instruct the agent to use it. Must be called before run().
     */
    public void enableRag() {
        super.enableRag(MethodToolCallbackProvider.builder().toolObjects(knowledgeBaseTool).build());
        super.systemPromptOverride(SYSTEM_PROMPT_RAG);
    }
}

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
 * Domain-specific agent — a personal academic-paper learning assistant.
 *
 * <p>Available tools (always): fetchWebPage, writeFile, readFile, terminate.
 * <p>Optional tool (RAG mode): searchKnowledgeBase — searches the user's personal
 * paper knowledge base; activated via {@link #enableRag()}.
 *
 * <p>Prototype-scoped so each request gets a fresh instance with clean state.
 * Inject via {@code ObjectProvider<InsightAnalyst>}.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InsightAnalyst extends ToolCallAgent {

    private static final String SYSTEM_PROMPT_BASE = """
            你是 InsightAgent，一个学术论文学习助手，帮助用户读懂、梳理和对比研究论文。
            可用工具：
              - fetchWebPage：抓取指定 URL 的网页内容（需要外部资料时）
              - writeFile：将整理结果保存到本地文件
              - readFile：读取已保存的本地文件
              - terminate：任务全部完成后调用此工具，将最终答案作为参数传入
            当前未连接论文知识库，请基于你已有的知识作答，并诚实说明哪些内容你无法确定。
            完成后调用 terminate 提交最终答案，中间步骤不要调用。
            """;

    private static final String SYSTEM_PROMPT_RAG = """
            你是 InsightAgent，一个学术论文学习助手，基于用户的【个人论文知识库】帮助其读懂、梳理和对比论文。
            可用工具：
              - searchKnowledgeBase：检索用户论文库中的相关片段（每次传一个聚焦的查询，返回内容带【来源：文件名】）
              - fetchWebPage：抓取指定 URL 的网页内容（仅在论文库不足、需要外部补充时）
              - writeFile：将整理结果保存到本地文件
              - readFile：读取已保存的本地文件
              - terminate：任务全部完成后调用此工具，将最终答案作为参数传入

            工作流程：
              1. 理解用户的问题（解释概念 / 总结某篇论文 / 跨论文对比与关联）
              2. 调用 searchKnowledgeBase 检索相关论文片段；问题涉及多篇论文或多个角度时，分多次检索
              3. 严格基于检索到的内容作答，并在结论处标注来源（如【来源：xxx.pdf】）
              4. 检索不到时如实说明"论文库中未找到相关内容"，不要凭空编造
              5. 调用 terminate 提交最终答案

            要求：先检索再作答；引用要落到具体来源；只有任务彻底完成时才调用 terminate。
            """;

    private final KnowledgeBaseTool knowledgeBaseTool;

    public InsightAnalyst(@Qualifier("deepSeekChatModel") ChatModel chatModel,
                          ToolCallbackProvider insightToolCallbackProvider,
                          TerminateTool terminateTool,
                          KnowledgeBaseTool knowledgeBaseTool) {
        super(ChatClient.builder(chatModel).build(),
                insightToolCallbackProvider,
                MethodToolCallbackProvider.builder().toolObjects(terminateTool).build(),
                SYSTEM_PROMPT_BASE, 12);
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

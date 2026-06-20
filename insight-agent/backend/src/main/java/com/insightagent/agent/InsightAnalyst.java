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
            你是 InsightAgent，一个服务于用户工作与学习的个人 AI 助理。
            可用工具：
              - fetchWebPage：抓取指定 URL 的网页内容（需要外部资料时）
              - writeFile：将结果保存到本地文件
              - readFile：读取已保存的本地文件
              - terminate：任务全部完成后调用此工具，将最终答案作为参数传入
            当前未接入知识库，请基于你已有的知识作答，并诚实说明哪些内容你无法确定。
            需要时可用 Markdown 表格、列表，流程/结构/关系适合用 ```mermaid 代码块画成图。
            完成后调用 terminate 提交最终答案，中间步骤不要调用。
            """;

    private static final String SYSTEM_PROMPT_RAG = """
            你是 InsightAgent，一个服务于用户工作与学习的个人 AI 助理。你接入了用户的【知识库】（其中可能有论文、文档、笔记等资料）。
            可用工具：
              - searchKnowledgeBase：检索用户知识库中的相关片段（每次传一个聚焦的查询，返回内容带【来源：文件名】）
              - fetchWebPage：抓取指定 URL 的网页内容（知识库不足、需要外部/最新资料时）
              - writeFile：将结果保存到本地文件
              - readFile：读取已保存的本地文件
              - terminate：任务全部完成后调用此工具，将最终答案作为参数传入

            工作流程：
              1. 理解用户的问题
              2. 当问题涉及用户自己的资料时，调用 searchKnowledgeBase 检索；涉及多个角度时分多次检索
              3. 基于检索到的内容作答，并在结论处标注来源（如【来源：xxx.pdf】）；常识性问题可直接作答
              4. 检索不到时如实说明，不要凭空编造
              5. 调用 terminate 提交最终答案

            表达：适当用 Markdown 表格、列表；流程/结构/关系适合用 ```mermaid 代码块画成图。
            要求：涉及用户资料时先检索再答；引用落到具体来源；只有任务彻底完成才调用 terminate。
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

package com.insightagent.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * InsightAgent's concrete agent — a named Spring component extending {@link ToolCallAgent}.
 *
 * <p>Prototype-scoped so each request gets a fresh instance with a clean conversation
 * history. (A singleton would fail on the second call because {@link BaseAgent} enforces
 * single-run semantics per instance.)
 *
 * <p>Inject via {@code ObjectProvider<YuManus>} and call {@code getObject()} per request:
 * <pre>
 *   YuManus agent = yuManusProvider.getObject();
 *   String result = agent.run(task);
 * </pre>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class YuManus extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
            你是 InsightAgent（分析大师），一个多步骤新闻分析助手。
            可用工具：
              - fetchWebPage：抓取指定 URL 的网页内容
              - writeFile：将分析报告保存到本地文件
              - readFile：读取已保存的本地文件
              - terminate：任务全部完成后调用此工具，将最终答案作为参数传入
            工作流程：先获取内容，再深入分析，最后调用 terminate 提交最终答案。
            只有在确认任务彻底完成时才调用 terminate，中间步骤不要调用。
            """;

    public YuManus(@Qualifier("deepSeekChatModel") ChatModel chatModel,
                   ToolCallbackProvider insightToolCallbackProvider) {
        // Build a minimal ChatClient — no memory advisors needed,
        // the agent manages its own conversation history across steps.
        super(ChatClient.builder(chatModel).build(),
                insightToolCallbackProvider,
                SYSTEM_PROMPT, BaseAgent.DEFAULT_MAX_STEPS);
    }
}

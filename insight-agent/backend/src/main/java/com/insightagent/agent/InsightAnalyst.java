package com.insightagent.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * InsightAgent 新闻分析师 — 基础档。
 *
 * <p>具体配置层：继承 {@link ToolCallAgent} 的通用机制，
 * 通过系统提示词和工具集定义"这个 Agent 的身份和能力边界"。
 *
 * <p>可用工具：fetchWebPage、writeFile、readFile、terminate
 *
 * <p>未来如需更强版本（多来源交叉验证、结构化报告输出等），
 * 新建 {@code InsightAnalystPro extends ToolCallAgent} 即可，
 * 机制层无需改动。
 *
 * <p>Prototype 作用域：每次请求获得新实例，避免状态污染。
 * 通过 {@code ObjectProvider<InsightAnalyst>} 注入。
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InsightAnalyst extends ToolCallAgent {

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

    public InsightAnalyst(@Qualifier("deepSeekChatModel") ChatModel chatModel,
                          ToolCallbackProvider insightToolCallbackProvider) {
        super(ChatClient.builder(chatModel).build(),
                insightToolCallbackProvider,
                SYSTEM_PROMPT, BaseAgent.DEFAULT_MAX_STEPS);
    }
}

package com.insightagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Terminal signal tool — the agent calls this to explicitly declare task completion.
 *
 * <p>Using a dedicated tool (rather than relying on "no tool calls = done") ensures
 * that the agent loop only ends when the model <em>intentionally</em> signals it is
 * finished, not because it produced a plain-text response mid-reasoning.
 *
 * <p>The model passes its complete final answer as {@code finalAnswer}; this method
 * returns it unchanged so {@link com.insightagent.agent.ToolCallAgent} can retrieve
 * it as the agent's output.
 */
@Component
public class TerminateTool {

    @Tool(description = """
            Call this tool when you have FULLY completed the task and are ready to deliver the final answer.
            Do NOT call this if you still need to fetch URLs, read files, or do further analysis.
            Pass your complete, well-structured final answer as the 'finalAnswer' parameter.
            """)
    public String terminate(
            @ToolParam(description = "Your complete final answer or analysis. This will be returned directly to the user.")
            String finalAnswer) {
        return finalAnswer;
    }
}

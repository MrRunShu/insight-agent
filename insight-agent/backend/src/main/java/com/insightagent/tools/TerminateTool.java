package com.insightagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class TerminateTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Tool(description = """
            Call this tool when you have FULLY completed the task and are ready to deliver the final answer.
            Do NOT call this if you still need to fetch URLs, read files, or do further analysis.
            Pass your complete, well-structured final answer as the 'finalAnswer' parameter.
            """)
    public String terminate(
            @ToolParam(description = "Your complete final answer or analysis. This will be returned directly to the user.")
            String finalAnswer) {
        log.info("[TerminateTool] received finalAnswer ({}): {}",
                finalAnswer != null ? finalAnswer.length() : 0,
                finalAnswer != null && finalAnswer.length() > 120
                        ? finalAnswer.substring(0, 120) + "…" : finalAnswer);
        // DeepSeek sometimes wraps the answer in a JSON object like
        // {"answer":"..."} or {"reason":"..."} or {"final_answer":"..."}.
        // Unwrap it so the user sees plain text / Markdown, not raw JSON.
        if (finalAnswer != null && finalAnswer.trim().startsWith("{")) {
            try {
                JsonNode node = MAPPER.readTree(finalAnswer);
                for (String key : new String[]{"answer", "final_answer", "finalAnswer", "reason", "result", "content"}) {
                    if (node.has(key)) {
                        String unwrapped = node.get(key).asText();
                        log.debug("[TerminateTool] unwrapped JSON key '{}' from finalAnswer", key);
                        return unwrapped;
                    }
                }
                // JSON but no known key — return as-is (might be structured data)
            } catch (Exception e) {
                // Not valid JSON — fall through and return as-is
            }
        }
        return finalAnswer;
    }
}

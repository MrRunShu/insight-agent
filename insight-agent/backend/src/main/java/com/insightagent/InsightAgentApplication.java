package com.insightagent;

import com.insightagent.tools.FileOperationTool;
import com.insightagent.tools.WebScrapeTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * InsightAgent Spring Boot entry point.
 *
 * <p>Registers a {@link ToolCallbackProvider} bean that exposes all {@code @Tool}-annotated
 * methods to both:
 * <ul>
 *   <li>The Spring AI ChatClient ({@code .tools(toolCallbackProvider)} in
 *       {@link com.insightagent.app.InsightApp})</li>
 *   <li>The MCP server ({@code spring-ai-starter-mcp-server-webmvc} picks it up
 *       automatically, exposing the tools at {@code GET /api/sse})</li>
 * </ul>
 */
@SpringBootApplication
public class InsightAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightAgentApplication.class, args);
    }

    /**
     * Registers WebScrapeTool and FileOperationTool for both ChatClient tool-calling
     * and the MCP server. The MCP server auto-discovers ToolCallbackProvider beans
     * and exposes their tools to any connected MCP client (Claude Desktop, Cursor, etc.).
     */
    @Bean
    public ToolCallbackProvider insightToolCallbackProvider(WebScrapeTool webScrapeTool,
                                                             FileOperationTool fileOperationTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(webScrapeTool, fileOperationTool)
                .build();
    }
}

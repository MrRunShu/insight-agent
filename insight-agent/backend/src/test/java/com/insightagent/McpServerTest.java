package com.insightagent;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the MCP server endpoint.
 *
 * Requires the application to be running on localhost:8123.
 * Run manually after starting the app.
 */
class McpServerTest {

    private static final String MCP_SSE_URL = "http://localhost:8123/api/sse";
    private static final String MCP_MSG_URL = "http://localhost:8123/api";

    @Test
    void mcpServerExposesThreeTools() {
        // Connect as an MCP client using SSE transport
        var transport = HttpClientSseClientTransport.builder(MCP_MSG_URL)
                .sseEndpoint("/sse")
                .build();

        try (McpSyncClient client = McpClient.sync(transport).build()) {
            // Initialize the MCP session
            McpSchema.InitializeResult init = client.initialize();
            System.out.println("Server: " + init.serverInfo().name()
                    + " v" + init.serverInfo().version());

            // List available tools
            McpSchema.ListToolsResult toolsResult = client.listTools();
            List<McpSchema.Tool> tools = toolsResult.tools();

            System.out.println("Tools found: " + tools.size());
            tools.forEach(t -> System.out.println("  - " + t.name() + ": " + t.description().substring(0, 40) + "..."));

            // Verify our three tools are registered
            List<String> toolNames = tools.stream().map(McpSchema.Tool::name).toList();
            assertThat(toolNames).contains("fetchWebPage", "writeFile", "readFile");
            assertThat(tools).hasSize(3);
        }
    }
}

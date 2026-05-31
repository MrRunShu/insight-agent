package com.insightagent.demo.invoke;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Way 4/4 — LangChain4j {@code OpenAiChatModel} pointed at DeepSeek's OpenAI-compatible API.
 *
 * Uses the upstream {@code langchain4j-open-ai} module with a custom baseUrl
 * so there is no dependency on any platform-specific SDK.
 */
public class LangChain4jDeepSeekInvoke {

    public static void main(String[] args) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("DEEPSEEK_API_KEY env var is not set.");
            return;
        }

        ChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .temperature(0.3)
                .maxTokens(256)
                .build();

        String reply = model.chat("Say 'InsightAgent online' and nothing else.");
        System.out.println("LangChain4j reply: " + reply);
    }
}

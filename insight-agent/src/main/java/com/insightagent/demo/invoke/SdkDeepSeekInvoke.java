package com.insightagent.demo.invoke;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatModel;

/**
 * Way 2/4 — official OpenAI Java SDK pointed at DeepSeek (DeepSeek is OpenAI-compatible).
 *
 * Uses the standard {@code openai-java} SDK with
 * a custom baseUrl because DeepSeek is the project's actual model.
 */
public class SdkDeepSeekInvoke {

    public static void main(String[] args) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("DEEPSEEK_API_KEY env var is not set.");
            return;
        }

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.deepseek.com")
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.of("deepseek-chat"))
                .temperature(0.3)
                .maxCompletionTokens(256L)
                .addSystemMessage("You are InsightAgent, a news analysis assistant.")
                .addUserMessage("Say 'InsightAgent online' and nothing else.")
                .build();

        ChatCompletion completion = client.chat().completions().create(params);
        completion.choices().stream()
                .findFirst()
                .flatMap(c -> c.message().content())
                .ifPresent(reply -> System.out.println("SDK reply: " + reply));
    }
}

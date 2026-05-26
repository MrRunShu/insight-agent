package com.insightagent.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * Way 1/4 — direct HTTP call to DeepSeek's OpenAI-compatible endpoint via Hutool.
 *
 * Run as a plain Java main:
 *   set DEEPSEEK_API_KEY=sk-...
 *   ./mvnw exec:java -Dexec.mainClass=com.insightagent.demo.invoke.HttpDeepSeekInvoke
 */
public class HttpDeepSeekInvoke {

    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";

    public static void main(String[] args) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("DEEPSEEK_API_KEY env var is not set.");
            return;
        }

        JSONObject body = JSONUtil.createObj()
                .set("model", "deepseek-chat")
                .set("temperature", 0.3)
                .set("max_tokens", 256)
                .set("messages", JSONUtil.createArray()
                        .set(JSONUtil.createObj()
                                .set("role", "system")
                                .set("content", "You are InsightAgent, a news analysis assistant."))
                        .set(JSONUtil.createObj()
                                .set("role", "user")
                                .set("content", "Say 'InsightAgent online' and nothing else.")));

        try (HttpResponse response = HttpRequest.post(ENDPOINT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(30_000)
                .execute()) {

            if (!response.isOk()) {
                System.err.println("Request failed: " + response.getStatus() + " " + response.body());
                return;
            }

            JSONObject json = JSONUtil.parseObj(response.body());
            String reply = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");
            System.out.println("HTTP reply: " + reply);
        }
    }
}

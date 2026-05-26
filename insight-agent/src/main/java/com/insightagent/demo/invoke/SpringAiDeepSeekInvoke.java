package com.insightagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;

/**
 * Way 3/4 — Spring AI ChatModel (auto-configured from {@code spring.ai.deepseek.*}).
 *
 * Uncomment {@code @Component} to run on every boot — handy for the first smoke test, but
 * leave it off in steady state so the bean is created lazily by feature code.
 *
 * Note: tutorial injects {@code dashscopeChatModel}; we inject the DeepSeek-named bean.
 */
// @org.springframework.stereotype.Component
public class SpringAiDeepSeekInvoke implements CommandLineRunner {

    @Resource
    private ChatModel deepseekChatModel;

    @Override
    public void run(String... args) {
        AssistantMessage output = deepseekChatModel
                .call(new Prompt("Say 'InsightAgent online' and nothing else."))
                .getResult()
                .getOutput();
        System.out.println("Spring AI (DeepSeek) reply: " + output.getText());
    }
}

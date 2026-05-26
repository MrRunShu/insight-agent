package com.insightagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;

/**
 * Extension - Spring AI calling a local Ollama model (tutorial section 2 part 5).
 *
 * Pre-req:
 *   ollama serve            (starts the daemon)
 *   ollama pull gemma3:1b   (downloads ~800MB)
 *
 * Spring AI Ollama starter is configured in application.yml under {@code spring.ai.ollama.chat}.
 * Uncomment {@code @Component} for a one-shot smoke test on boot.
 */
// @org.springframework.stereotype.Component
public class OllamaInvoke implements CommandLineRunner {

    @Resource
    private ChatModel ollamaChatModel;

    @Override
    public void run(String... args) {
        AssistantMessage output = ollamaChatModel
                .call(new Prompt("Reply with one word: 'ready'."))
                .getResult()
                .getOutput();
        System.out.println("Ollama (gemma3) reply: " + output.getText());
    }
}

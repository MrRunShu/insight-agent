package com.insightagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * INFO-level logger advisor. Spring AI ships {@code SimpleLoggerAdvisor} but it logs at DEBUG.
 * This one logs the last user message + assistant text + token usage at INFO.
 *
 * <p>Implements {@link BaseAdvisor} so we get default {@code adviseCall}/{@code adviseStream}
 * for free — only {@code before}/{@code after} need to be implemented.
 */
@Slf4j
public class LoggerAdvisor implements BaseAdvisor {

    private static final int ORDER = -1000;

    @Override
    public String getName() {
        return "LoggerAdvisor";
    }

    @Override
    public int getOrder() {
        // run very early so we log the original request, before any other advisor rewrites it
        return ORDER;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        Prompt prompt = request.prompt();
        String userText = prompt.getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).getText())
                .reduce((a, b) -> b) // last user message
                .orElse("");
        log.info("[chat>] {}", truncate(userText, 200));
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        var chatResponse = response.chatResponse();
        if (chatResponse != null) {
            String assistantText = chatResponse.getResult() != null
                    ? chatResponse.getResult().getOutput().getText()
                    : "(no output)";
            Usage usage = chatResponse.getMetadata() != null
                    ? chatResponse.getMetadata().getUsage()
                    : null;
            if (usage != null) {
                log.info("[chat<] {} (in={}, out={}, total={})",
                        truncate(assistantText, 200),
                        usage.getPromptTokens(),
                        usage.getCompletionTokens(),
                        usage.getTotalTokens());
            } else {
                log.info("[chat<] {}", truncate(assistantText, 200));
            }
        }
        return response;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}

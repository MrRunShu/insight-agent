package com.insightagent.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-Reading (Re2) advisor. Re2 is a published technique that boosts reasoning quality by
 * making the model re-read the question before answering: rewrite the user prompt to
 * {@code "{q} Read the question again: {q}"}.
 *
 * <p><strong>Cost warning</strong>: input tokens roughly double. Don't enable for C-end users
 * or chatty workflows. Default-off in {@code InsightApp}; turned on for the Tier 3 deep
 * analyst sub-agents where reasoning quality matters more than per-call cost.
 */
public class ReReadingAdvisor implements BaseAdvisor {

    private static final String SUFFIX = "\nRead the question again: ";

    @Override
    public String getName() {
        return "ReReadingAdvisor";
    }

    @Override
    public int getOrder() {
        // run after memory advisor (which is high-order) so we rewrite the resolved user prompt
        return 0;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        Prompt prompt = request.prompt();
        List<Message> messages = new ArrayList<>(prompt.getInstructions());

        // find last user message and rewrite it in place
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m instanceof UserMessage userMsg) {
                String original = userMsg.getText();
                if (original == null || original.isBlank()) break;
                String rewritten = original + SUFFIX + original;
                messages.set(i, new UserMessage(rewritten));
                break;
            }
        }
        Prompt rewritten = new Prompt(messages, prompt.getOptions());
        return request.mutate().prompt(rewritten).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }
}

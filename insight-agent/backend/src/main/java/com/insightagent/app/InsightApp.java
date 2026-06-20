package com.insightagent.app;

import com.insightagent.agent.InsightAnalyst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Service facade for the personal-assistant agent.
 *
 * <p>Each request gets a fresh prototype-scoped {@link InsightAnalyst} (clean
 * conversation history) which runs the ReAct think → act loop, optionally with
 * RAG over the user's knowledge base, streaming each step as an SSE event.
 */
@Component
@Slf4j
public class InsightApp {

    /** Prototype provider — each call to getObject() returns a fresh InsightAnalyst instance. */
    @Autowired
    private ObjectProvider<InsightAnalyst> insightAnalystProvider;

    /**
     * Run the ReAct agent loop and stream each step as a Server-Sent Event.
     *
     * @param message         user task / question
     * @param selectedSnippet optional snippet folded into the first user message
     * @param ragEnabled      whether to search the knowledge base during the run
     * @return {@link SseEmitter} streaming step events and a final "done" event
     */
    public SseEmitter doRunAgentStream(String message, String selectedSnippet, boolean ragEnabled) {
        InsightAnalyst agent = insightAnalystProvider.getObject();
        if (ragEnabled) {
            agent.enableRag();
            log.info("[InsightApp] RAG mode enabled for this agent run");
        }
        return agent.runStream(buildUserText(message, selectedSnippet));
    }

    private String buildUserText(String message, String selectedSnippet) {
        return (selectedSnippet == null || selectedSnippet.isBlank())
                ? message
                : message + "\n\n[USER-SELECTED SNIPPET]\n" + selectedSnippet;
    }
}

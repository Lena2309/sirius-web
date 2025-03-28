package org.eclipse.sirius.web.ai.tool.service;

import org.slf4j.Logger;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ToolCallService {

    public ToolCallService() {
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                    ORCHESTRATOR
    // ---------------------------------------------------------------------------------------------------------------

    public void computeToolCalls(Logger logger, ChatModel chatModel, Prompt prompt, ThreadPoolTaskExecutor taskExecutor, Object... tools) {
        var latch = new AtomicReference<>(new CountDownLatch(0));
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(requestAttributes, true);

        var toolCallingManager = ToolCallingManager.builder().build();

        var chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(tools))
                .internalToolExecutionEnabled(false)
                .build();

        prompt = new Prompt(prompt.getInstructions(), chatOptions);

        var response = chatModel.call(prompt);

        while (response.hasToolCalls()) {
            AtomicReference<ToolExecutionResult> toolExecutionResult = new AtomicReference<>();

            latch.set(new CountDownLatch((int) (latch.get().getCount()+1)));
            var threadResponse = response;
            var threadPrompt = prompt;
            taskExecutor.execute(() -> {
                try {
                    toolExecutionResult.set(toolCallingManager.executeToolCalls(threadPrompt, threadResponse));
                } finally {
                    latch.get().countDown();
                }
            });
            try {
                latch.get().await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Main thread interrupted while waiting for workers", e);
            }

            prompt = new Prompt(toolExecutionResult.get().conversationHistory(), chatOptions);

            Instant responseStart = Instant.now();
            response = chatModel.call(prompt);
            Instant responseFinish = Instant.now();

            long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
            logger.info("Assistant answered in {} ms", responseDuration);
        }
    }
}

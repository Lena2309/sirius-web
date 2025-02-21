package org.eclipse.sirius.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.eclipse.sirius.ai.agent.Agent;
import org.eclipse.sirius.ai.tool.AiTool;
import org.slf4j.Logger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.MethodInvoker;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ToolCallService {
    static StringBuilder agentsResults = new StringBuilder();

    public static void computeToolCalls(Logger logger, ChatLanguageModel model, List<ChatMessage> previousMessages, List<AiTool> aiTools, List<ToolSpecification> specifications) {
        var response = model.generate(previousMessages, specifications);

        while (response.content().hasToolExecutionRequests()) {
            previousMessages.add(response.content());
            logger.info(response.content().toolExecutionRequests().toString());

            for (var toolExecutionRequest : response.content().toolExecutionRequests()) {
                var toolExecutionResultMessage = parseAndExecuteToolExecutionRequests(logger, aiTools, toolExecutionRequest);

                logger.info("tool execution result : {}", toolExecutionResultMessage.text());

                previousMessages.add(toolExecutionResultMessage);
            }

            Instant responseStart = Instant.now();
            response = model.generate(previousMessages, specifications);
            Instant responseFinish = Instant.now();

            long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
            logger.warn("Assistant answered in {} ms", responseDuration);
        }
    }

    public static void computeToolCalls(Logger logger, ChatLanguageModel model, List<ChatMessage> previousMessages, List<AiTool> aiTools, List<ToolSpecification > specifications, List<Agent> agents, ThreadPoolTaskExecutor taskExecutor) {
        var latch = new AtomicReference<>(new CountDownLatch(0));
        var response = model.generate(previousMessages, specifications);

        var requestAttributes = RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(requestAttributes, true);

        while (response.content().hasToolExecutionRequests()) {

            previousMessages.add(response.content());
            logger.info(response.content().toolExecutionRequests().toString());

            for (var toolExecutionRequest : response.content().toolExecutionRequests()) {
                var toolExecutionResultMessage = parseAndExecuteToolExecutionRequests(logger, aiTools, toolExecutionRequest, agents, taskExecutor, latch);
                logger.info("tool execution result : {}", toolExecutionResultMessage.text());

                previousMessages.add(toolExecutionResultMessage);
            }

            Instant responseStart = Instant.now();
            response = model.generate(previousMessages, specifications);
            Instant responseFinish = Instant.now();

            long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
            logger.warn("Assistant answered in {} ms", responseDuration);

            try {
                latch.get().await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Main thread interrupted while waiting for workers", e);
            }
        }

        if (!agentsResults.isEmpty()) {
            logger.info(agentsResults.toString());
            previousMessages.add(new UserMessage(agentsResults.toString()));
            agentsResults.setLength(0);
        }
    }

    private static ToolExecutionResultMessage parseAndExecuteToolExecutionRequests(Logger logger, List<AiTool> aiTools, ToolExecutionRequest toolExecutionRequest) {
            return tryAiToolExecution(logger, aiTools, toolExecutionRequest);
    }

    private static ToolExecutionResultMessage parseAndExecuteToolExecutionRequests(Logger logger, List<AiTool> aiTools, ToolExecutionRequest toolExecutionRequest, List<Agent> agents, ThreadPoolTaskExecutor taskExecutor, AtomicReference<CountDownLatch> latch) {
        var toolExecutionResultMessage = tryAiToolExecution(logger, aiTools, toolExecutionRequest);
        if (toolExecutionResultMessage != null) return toolExecutionResultMessage;

        tryToolAgentExecution(logger, agents, toolExecutionRequest, taskExecutor, latch);

        return ToolExecutionResultMessage.from(toolExecutionRequest, "Agent called successfully.");
    }

    private static ToolExecutionResultMessage tryAiToolExecution(Logger logger, List<AiTool> aiTools, ToolExecutionRequest toolExecutionRequest) {
        for (AiTool aiTool : aiTools) {
            try {
                var methodInvoker = instanciateMethodInvoker(aiTool, toolExecutionRequest);

                methodInvoker.prepare();

                var result = methodInvoker.invoke();

                assert result != null;
                return ToolExecutionResultMessage.from(toolExecutionRequest, result.toString());
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                logger.warn("Error while trying to call tools: ", e);
            }
        }
        return null;
    }

    private static void tryToolAgentExecution(Logger logger, List<Agent> agents, ToolExecutionRequest toolExecutionRequest, ThreadPoolTaskExecutor taskExecutor, AtomicReference<CountDownLatch> latch) {
        for (var agent : agents) {
            taskExecutor.execute(() -> {
                latch.set(new CountDownLatch((int) (latch.get().getCount()+1)));
                try {
                    var methodInvoker = instanciateMethodInvoker(agent, toolExecutionRequest);

                    methodInvoker.prepare();

                    var result = methodInvoker.invoke();

                    assert result != null;
                    logger.info(result.toString());
                    agentsResults.append("Tool ").append(toolExecutionRequest.name()).append(" answered with: ").append(result);
                } catch (Exception ignored) {
                } finally {
                    latch.get().countDown();
                }
            });
        }
    }

    private static MethodInvoker instanciateMethodInvoker(Object tool, ToolExecutionRequest toolExecutionRequest) throws Exception {
        var methodInvoker = new MethodInvoker();

        methodInvoker.setTargetObject(tool);

        methodInvoker.setTargetMethod(toolExecutionRequest.name());

        var toolArguments = parseJsonToMap(toolExecutionRequest.arguments());
        if (!toolArguments.isEmpty()) {
            methodInvoker.setArguments(toolArguments.values().toArray());
        }

        return methodInvoker;
    }

    private static Map<String, Object> parseJsonToMap(String jsonString) throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, Map.class);
    }
}

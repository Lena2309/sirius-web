package org.eclipse.sirius.web.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.eclipse.sirius.web.ai.agent.Agent;
import org.eclipse.sirius.web.ai.configuration.BlockingRateLimiter;
import org.eclipse.sirius.web.ai.dto.AgentResult;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.slf4j.Logger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.MethodInvoker;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ToolCallService {
    //private static volatile List<ToolExecutionResultMessage> agentsOutputs = new ArrayList<>();

    // ---------------------------------------------------------------------------------------------------------------
    //                                                    ORCHESTRATOR
    // ---------------------------------------------------------------------------------------------------------------

    public static void computeToolCalls(Logger logger, ChatLanguageModel model, List<ChatMessage> previousMessages, List<ToolSpecification > specifications, List<Agent> agents, ThreadPoolTaskExecutor taskExecutor, BlockingRateLimiter rateLimiter) {
        var latch = new AtomicReference<>(new CountDownLatch(0));
        var agentsOutputs = new ArrayList<ToolExecutionResultMessage>();

        //logger.info("Rate limit is " + rateLimiter.getPermits());
        rateLimiter.acquire(logger);
        //noinspection removal
        var response = model.generate(previousMessages, specifications);

        var requestAttributes = RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(requestAttributes, true);

        while (response.content().hasToolExecutionRequests()) {

            previousMessages.add(response.content());
            logger.info(response.content().toolExecutionRequests().toString());

            for (var toolExecutionRequest : response.content().toolExecutionRequests()) {
                tryToolAgentExecution(logger, agents, toolExecutionRequest, agentsOutputs, taskExecutor, latch);
            }

            try {
                latch.get().await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Main thread interrupted while waiting for workers", e);
            }

            if (!agentsOutputs.isEmpty()) {
                logger.info(agentsOutputs.toString());
                previousMessages.addAll(agentsOutputs);
                agentsOutputs.clear();
            }

            //logger.info("Rate limit is " + rateLimiter.getPermits());
            rateLimiter.acquire(logger);

            Instant responseStart = Instant.now();
            //noinspection removal
            response = model.generate(previousMessages, specifications);
            Instant responseFinish = Instant.now();

            long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
            logger.info("Assistant answered in {} ms", responseDuration);
        }
    }

    private static void tryToolAgentExecution(Logger logger, List<Agent> agents, ToolExecutionRequest toolExecutionRequest, List<ToolExecutionResultMessage> agentsOutputs, ThreadPoolTaskExecutor taskExecutor, AtomicReference<CountDownLatch> latch) {
        for (var agent : agents) {
            latch.set(new CountDownLatch((int) (latch.get().getCount()+1)));
            taskExecutor.execute(() -> {
                try {
                    var methodInvoker = instanciateMethodInvoker(agent, toolExecutionRequest);

                    methodInvoker.prepare();

                    var result = methodInvoker.invoke();

                    logger.info("Agent Result : {}", result);
                    Objects.requireNonNull(result);

                    synchronized(agentsOutputs) {
                        agentsOutputs.add(ToolExecutionResultMessage.from(toolExecutionRequest, result.toString()));
                    }
                } catch (Exception e) {
                    if (e.getCause() instanceof UnsupportedOperationException unsupported) {
                        agentsOutputs.add(ToolExecutionResultMessage.from(toolExecutionRequest, unsupported.getMessage()));
                    } else if (!(e instanceof NoSuchMethodException)){
                        logger.error(e.getMessage(), e);
                    }
                } finally {
                    latch.get().countDown();
                }
            });
        }
    }


    // ---------------------------------------------------------------------------------------------------------------
    //                                                      TOOL AGENTS
    // ---------------------------------------------------------------------------------------------------------------

    public static void computeToolCalls(Logger logger, ChatLanguageModel model, List<ChatMessage> previousMessages, List<AiTool> aiTools, List<ToolSpecification> specifications, List<AgentResult> toolResults, BlockingRateLimiter rateLimiter) {
        //logger.info("Rate limit is " + rateLimiter.getPermits());
        rateLimiter.acquire(logger);
        //noinspection removal
        var response = model.generate(previousMessages, specifications);

        while (response.content().hasToolExecutionRequests()) {
            previousMessages.add(response.content());
            logger.info(response.content().toolExecutionRequests().toString());

            for (var toolExecutionRequest : response.content().toolExecutionRequests()) {
                var toolExecutionResultMessage = tryAiToolExecution(logger, aiTools, toolExecutionRequest, toolResults);

                logger.info("tool execution result : {}", toolExecutionResultMessage.text());

                previousMessages.add(toolExecutionResultMessage);
            }

            //logger.info("Rate limit is " + rateLimiter.getPermits());
            rateLimiter.acquire(logger);

            Instant responseStart = Instant.now();
            //noinspection removal
            response = model.generate(previousMessages, specifications);
            Instant responseFinish = Instant.now();

            long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
            logger.info("Assistant answered in {} ms", responseDuration);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  METHOD INVOKER
    // ---------------------------------------------------------------------------------------------------------------

    private static ToolExecutionResultMessage tryAiToolExecution(Logger logger, List<AiTool> aiTools, ToolExecutionRequest toolExecutionRequest, List<AgentResult> toolResult) {
        var toolExecutionResult = ToolExecutionResultMessage.from(toolExecutionRequest, "A problem occurred. Try again.");
        for (AiTool aiTool : aiTools) {
            try {
                var methodInvoker = instanciateMethodInvoker(aiTool, toolExecutionRequest);

                methodInvoker.prepare();

                var result = methodInvoker.invoke();

                Objects.requireNonNull(result);
                if (result instanceof AgentResult agentResult) {
                    toolResult.add(agentResult);
                }

                return ToolExecutionResultMessage.from(toolExecutionRequest, result.toString());
            } catch (Exception e) {
                if (e.getCause() instanceof UnsupportedOperationException unsupported) {
                    return ToolExecutionResultMessage.from(toolExecutionRequest, unsupported.getMessage());
                } else if (!(e instanceof NoSuchMethodException)){
                    logger.error(e.getMessage(), e);
                }
            }
        }
        return toolExecutionResult;
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

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  PARSERS
    // ---------------------------------------------------------------------------------------------------------------

    private static Map<String, Object> parseJsonToMap(String jsonString) throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, Map.class);
    }
}

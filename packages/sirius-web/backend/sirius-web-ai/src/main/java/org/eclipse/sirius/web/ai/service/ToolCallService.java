package org.eclipse.sirius.web.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
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

    // ---------------------------------------------------------------------------------------------------------------
    //                                                    ORCHESTRATOR
    // ---------------------------------------------------------------------------------------------------------------

    public static void computeToolCalls(Logger logger, ChatLanguageModel model, ChatRequest chatRequest, List<Agent> agents, List<AiTool> tools, List<AgentResult> toolResults, ThreadPoolTaskExecutor taskExecutor, BlockingRateLimiter rateLimiter) {
        var latch = new AtomicReference<>(new CountDownLatch(0));
        var agentsOutputs = new ArrayList<ToolExecutionResultMessage>();

        //logger.info("Rate limit is " + rateLimiter.getPermits());
        rateLimiter.acquire(logger);
        var response = model.chat(chatRequest).aiMessage();

        var requestAttributes = RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(requestAttributes, true);

        while (response.hasToolExecutionRequests()) {

            chatRequest.messages().add(response);
            logger.info(response.toolExecutionRequests().toString());

            for (var toolExecutionRequest : response.toolExecutionRequests()) {
                tryToolAgentExecution(logger, agents, toolExecutionRequest, agentsOutputs, taskExecutor, latch);

                if (!tools.isEmpty()) {
                    var toolExecutionResultMessage = tryAiToolExecution(logger, tools, toolExecutionRequest, toolResults);
                    if (!Objects.equals(toolExecutionResultMessage.text(), "Tool Execution is not for available tools.")) {
                        logger.info("tool execution result : {}", toolExecutionResultMessage.text());
                        chatRequest.messages().add(toolExecutionResultMessage);
                    }
                }
            }

            try {
                latch.get().await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Main thread interrupted while waiting for workers", e);
            }

            if (!agentsOutputs.isEmpty()) {
                logger.info(agentsOutputs.toString());
                chatRequest.messages().addAll(agentsOutputs);
                agentsOutputs.clear();
            }

            //logger.info("Rate limit is " + rateLimiter.getPermits());
            rateLimiter.acquire(logger);

            Instant responseStart = Instant.now();
            response = model.chat(chatRequest).aiMessage();
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

    public static void computeToolCalls(Logger logger, ChatLanguageModel model, ChatRequest chatRequest, List<AiTool> aiTools, List<AgentResult> toolResults, BlockingRateLimiter rateLimiter) {
        //logger.info("Rate limit is " + rateLimiter.getPermits());
        rateLimiter.acquire(logger);
        var response = model.chat(chatRequest).aiMessage();

        while (response.hasToolExecutionRequests()) {
            chatRequest.messages().add(response);
            logger.info(response.toolExecutionRequests().toString());

            for (var toolExecutionRequest : response.toolExecutionRequests()) {
                var toolExecutionResultMessage = tryAiToolExecution(logger, aiTools, toolExecutionRequest, toolResults);
                logger.info("tool execution result : {}", toolExecutionResultMessage.text());
                chatRequest.messages().add(toolExecutionResultMessage);
            }

            //logger.info("Rate limit is " + rateLimiter.getPermits());
            rateLimiter.acquire(logger);

            Instant responseStart = Instant.now();
            response = model.chat(chatRequest).aiMessage();
            Instant responseFinish = Instant.now();

            long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
            logger.info("Assistant answered in {} ms", responseDuration);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  METHOD INVOKER
    // ---------------------------------------------------------------------------------------------------------------

    private static ToolExecutionResultMessage tryAiToolExecution(Logger logger, List<AiTool> aiTools, ToolExecutionRequest toolExecutionRequest, List<AgentResult> toolResult) {
        var toolExecutionResult = ToolExecutionResultMessage.from(toolExecutionRequest, "Tool Execution is not for available tools.");
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
                    return ToolExecutionResultMessage.from(toolExecutionRequest, e.getMessage());
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

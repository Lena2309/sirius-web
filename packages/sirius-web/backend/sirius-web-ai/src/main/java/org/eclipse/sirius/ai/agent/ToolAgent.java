package org.eclipse.sirius.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.tool.AiTools;
import org.eclipse.sirius.ai.tool.getter.LinkGetterTools;
import org.eclipse.sirius.ai.tool.getter.ObjectGetterTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MethodInvoker;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ToolAgent {
    protected final ChatLanguageModel model;

    protected final List<AiTools> toolClasses = new ArrayList<>();

    protected Logger logger = null;

    public ToolAgent(ChatLanguageModel model,
                     ObjectGetterTools objectGetterTools,
                     LinkGetterTools linkGetterTools) {
        this.model = model;
        this.toolClasses.add(objectGetterTools);
        this.toolClasses.add(linkGetterTools);
    }

    public void setLogger(ToolAgent agent) {
        this.logger = LoggerFactory.getLogger(agent.getClass());
    }

    public void setInput(IInput input) {
        if (input instanceof AiRequestInput aiRequestInput) {
            this.toolClasses.forEach(aiTools -> aiTools.setInput(aiRequestInput));
        }
    }

    protected void computeToolCalls(List<ChatMessage> previousMessages, List<ToolSpecification > specifications) {
        Instant start = Instant.now();
        var response = this.model.generate(previousMessages, specifications);
        Instant finish = Instant.now();

        long duration = Duration.between(start, finish).toMillis();
        logger.warn("Generated reasonAgent message in {} ms", duration);

        while (response.content().hasToolExecutionRequests()) {
            previousMessages.add(response.content());
            logger.info(response.content().toolExecutionRequests().toString());

            for (var toolExecutionRequest : response.content().toolExecutionRequests()) {
                var toolExecutionResultMessage = this.parseAndExecuteToolExecutionRequests(toolExecutionRequest);
                logger.info("tool execution result : {}", toolExecutionResultMessage.text());

                previousMessages.add(toolExecutionResultMessage);
            }

            Instant responseStart = Instant.now();
            response = this.model.generate(previousMessages, specifications);
            Instant responseFinish = Instant.now();

            long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
            logger.warn("Assistant answered in {} ms", responseDuration);
        }
    }

    protected ToolExecutionResultMessage parseAndExecuteToolExecutionRequests(ToolExecutionRequest toolExecutionRequest) {
        for (AiTools aiTool : this.toolClasses) {
            try {
                var methodInvoker = this.instanciateMethodInvoker(aiTool, toolExecutionRequest);

                methodInvoker.prepare();

                var result = methodInvoker.invoke();

                assert result != null;
                return ToolExecutionResultMessage.from(toolExecutionRequest, result.toString());
            } catch (Exception ignored) {}
        }
        return ToolExecutionResultMessage.from(toolExecutionRequest, "Error while trying to call tools. The arguments may be incorrect.");
    }

    protected MethodInvoker instanciateMethodInvoker(AiTools toolClass, ToolExecutionRequest toolExecutionRequest) throws Exception {
        var methodInvoker = new MethodInvoker();

        methodInvoker.setTargetObject(toolClass);

        methodInvoker.setTargetMethod(toolExecutionRequest.name());

        var toolArguments = this.parseJsonToMap(toolExecutionRequest.arguments());
        if (!toolArguments.isEmpty()) {
            methodInvoker.setArguments(toolArguments.values().toArray());
        }

        return methodInvoker;
    }

    protected Map<String, Object> parseJsonToMap(String jsonString) throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, Map.class);
    }
}

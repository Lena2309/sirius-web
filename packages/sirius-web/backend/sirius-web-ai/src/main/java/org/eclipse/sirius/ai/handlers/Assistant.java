package org.eclipse.sirius.ai.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.util.JsonParser;
import org.eclipse.sirius.ai.tools.*;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MethodInvoker;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class Assistant {

    private final Logger logger = LoggerFactory.getLogger(Assistant.class);

    private final ChatLanguageModel model;

    private final List<ToolSpecification> objectToolsSpecifications = ToolSpecifications.toolSpecificationsFrom(AiObjectTools.class);

    private final List<ToolSpecification> edgeToolsSpecifications = ToolSpecifications.toolSpecificationsFrom(AiLinkTools.class);

    private final AiObjectTools aiObjectTools;

    private final AiLinkTools aiLinkTools;

    private List<ChatMessage> previousMessages = new ArrayList<>();

    public Assistant(ChatLanguageModel model, AiObjectTools aiObjectTools, AiLinkTools aiLinkTools) {
        this.model = model;
        this.aiObjectTools = aiObjectTools;
        this.aiLinkTools = aiLinkTools;
    }

    public void generate(IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            this.aiObjectTools.setInput(aiRequestInput);
            this.aiLinkTools.setInput(aiRequestInput);

            List<ToolSpecification> specifications = objectToolsSpecifications;
            specifications.addAll(edgeToolsSpecifications);

            SystemMessage systemMessage = new SystemMessage("""
            You are an assistant for Diagram Generation. 
            Use the available tools, you must use the correct tools, do not hallucinate. 
            The user's request may need a tool's execution. 
            When generating diagram elements, add relevant links between the objects.
            You must write in text the type of tool you need for your next request : OBJECT_TOOLS or LINK_TOOLS. At first you will have the object tools.
            """
            );
            UserMessage userMessage = new UserMessage(aiRequestInput.request());

            this.previousMessages.add(systemMessage);
            this.previousMessages.add(userMessage);

            Instant  start = Instant.now();
            Response<AiMessage> response = this.model.generate(this.previousMessages, specifications);
            Instant finish = Instant.now();

            long duration = Duration.between(start, finish).toMillis();
            logger.warn("Generated assistant message in {} ms", duration);

            while (response.content().hasToolExecutionRequests()) {
                this.previousMessages.add(response.content());
                logger.info(response.content().toString());

                for (ToolExecutionRequest toolExecutionRequest : response.content().toolExecutionRequests()) {

                    Instant  toolStart= Instant.now();
                    ToolExecutionResultMessage  toolExecutionResultMessage = this.parseAndExecuteToolExecutionRequests(toolExecutionRequest, aiRequestInput);
                    Instant toolFinish = Instant.now();

                    long toolDuration = Duration.between(toolStart, toolFinish).toMillis();
                    logger.warn("Tool call: {} ms", toolDuration);

                    logger.info(toolExecutionResultMessage.toString());

                    this.previousMessages.add(toolExecutionResultMessage);
                }

                if (!getRelevantTools(response.content().toString()).isEmpty()) {
                    specifications = getRelevantTools(response.content().toString());
                }

                Instant  responseStart = Instant.now();
                response = this.model.generate(this.previousMessages, specifications);
                Instant responseFinish = Instant.now();

                long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
                logger.warn("Assistant answered in {} ms", responseDuration);
            }
        }
    }

    private ToolExecutionResultMessage parseAndExecuteToolExecutionRequests(ToolExecutionRequest toolExecutionRequest, IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            try {
                MethodInvoker methodInvoker = this.instanciateMethodInvoker(aiObjectTools, toolExecutionRequest);

                methodInvoker.prepare();

                var result = methodInvoker.invoke();

                assert result != null;
                return ToolExecutionResultMessage.from(toolExecutionRequest, result.toString());
            } catch (Exception ignored) {
                try {
                    MethodInvoker methodInvoker = this.instanciateMethodInvoker(aiLinkTools, toolExecutionRequest);

                    methodInvoker.prepare();

                    var result = methodInvoker.invoke();

                    assert result != null;
                    return ToolExecutionResultMessage.from(toolExecutionRequest, result.toString());
                } catch (Exception e) {
                    return ToolExecutionResultMessage.from(toolExecutionRequest, e.toString());
                }
            }
        }
        throw new Exception("Input not of type AiRequestInput.");
    }

    private MethodInvoker instanciateMethodInvoker(AiTools toolClass, ToolExecutionRequest toolExecutionRequest) throws JsonProcessingException {
        MethodInvoker methodInvoker = new MethodInvoker();

        methodInvoker.setTargetObject(toolClass);

        methodInvoker.setTargetMethod(toolExecutionRequest.name());

        Map<String, Object> toolArguments = JsonParser.parseJsonToMap(toolExecutionRequest.arguments());
        if (!toolArguments.isEmpty()) {
            methodInvoker.setArguments(toolArguments.values().toArray());
        }

        return methodInvoker;
    }

    private List<ToolSpecification> getRelevantTools(String toolType) {
        return switch (toolType) {
            case "OBJECT_TOOLS" -> this.objectToolsSpecifications;
            case "LINK_TOOLS" -> this.edgeToolsSpecifications;
            default -> List.of();
        };
    }

}

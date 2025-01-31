package org.eclipse.sirius.ai.handlers;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.parser.JsonParser;
import org.eclipse.sirius.ai.tools.AssistantElementTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MethodInvoker;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Service
public class Assistant {

    private final Logger logger = LoggerFactory.getLogger(Assistant.class);

    private final ChatLanguageModel model;

    private final List<ToolSpecification> toolSpecifications = ToolSpecifications.toolSpecificationsFrom(AssistantElementTools.class);

    private final AssistantElementTools assistantElementTools;

    private List<ChatMessage> previousMessages = new ArrayList<>();

    public Assistant(ChatLanguageModel model, AssistantElementTools assistantElementTools) {
        this.model = model;
        this.assistantElementTools = assistantElementTools;
    }

    public void generate(IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            SystemMessage systemMessage = new SystemMessage("You are an assistant for Diagram Generation. Use the available tools, you must use the correct tools, do not invent random information.");
            UserMessage userMessage = new UserMessage(aiRequestInput.request());

            this.previousMessages.add(systemMessage);
            this.previousMessages.add(userMessage);

            Response<AiMessage> response = this.model.generate(this.previousMessages, this.toolSpecifications);

            while (response.content().hasToolExecutionRequests()) {

                this.previousMessages.add(response.content());

                logger.info(response.content().toString());

                for (ToolExecutionRequest toolExecutionRequest : response.content().toolExecutionRequests()) {
                    ToolExecutionResultMessage  toolExecutionResultMessage = this.parseAndExecuteToolExecutionRequests(toolExecutionRequest, aiRequestInput);
                    this.previousMessages.add(toolExecutionResultMessage);
                }

                response = this.model.generate(this.previousMessages, this.toolSpecifications);
            }
        }
    }

    private ToolExecutionResultMessage parseAndExecuteToolExecutionRequests(ToolExecutionRequest toolExecutionRequest, IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {

            Map<String, Object> toolArguments = JsonParser.parseJsonToMap(toolExecutionRequest.arguments());


            toolArguments.forEach((k, v) -> {
                if (v instanceof LinkedHashMap) {
                    toolArguments.replace(k, aiRequestInput);
                }
            });

            try {
                MethodInvoker methodInvoker = new MethodInvoker();
                methodInvoker.setTargetObject(this.assistantElementTools);
                methodInvoker.setTargetMethod(toolExecutionRequest.name());

                if (!toolArguments.isEmpty()) {
                    methodInvoker.setArguments(toolArguments.values().toArray());
                }

                methodInvoker.prepare();

                var result = methodInvoker.invoke();

                assert result != null;
                return ToolExecutionResultMessage.from(toolExecutionRequest, result.toString());
            } catch (Exception e) {
                return ToolExecutionResultMessage.from(toolExecutionRequest, e.toString());
            }
        }
        throw new Exception("Input not of type AiRequestInput.");
    }
}

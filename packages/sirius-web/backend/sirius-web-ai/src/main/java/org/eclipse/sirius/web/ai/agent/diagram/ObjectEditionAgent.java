package org.eclipse.sirius.web.ai.agent.diagram;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.dto.AgentResult;
import org.eclipse.sirius.web.ai.service.ToolCallService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.edition.ObjectEditionTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.EDITION_MODEL;

@Service
public class ObjectEditionAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ObjectEditionAgent.class);

    private final ChatLanguageModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private IInput input;

    public ObjectEditionAgent(ObjectEditionTools objectEditionTools) {
        this.model = AiModelsConfiguration.buildLanguageModel(EDITION_MODEL);
        this.toolClasses.add(objectEditionTools);
    }

    @Override
    public void setInput(IInput input) {
        this.input = input;
    }

    @Override
    public void setToolsInput() {
        for (AiTool toolClass : toolClasses) {
            toolClass.setInput(this.input);
        }
    }

    @Tool("Edit an object's properties.")
    public String editObjectProperties(@P("Explain what properties to modify with their new values.") String prompt, @P("The object id to edit, the id is in a format similar to \"AbcdEF+GhijKLM1NOpqrS==\".") String objectId) {
        var rateLimiter = AiModelsConfiguration.getRateLimiter(this.model);
        var specifications = new ArrayList<>(initializeSpecifications(List.of(), this.input, this.toolClasses));
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Diagram Object Edition.
            Do not write any text, just call the correct tools to edit the correct diagram element given in the user's request.
            Do not hallucinate, do not invent properties and pay attention to their types.
            """
        );

        var chatRequest = ChatRequest.builder()
                .messages(List.of(systemMessage, new UserMessage("Here is the object to edit: " + objectId + ". " + prompt)))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(specifications)
                        .build())
                .build();

        var results = new ArrayList<AgentResult>();
        ToolCallService.computeToolCalls(logger, this.model, chatRequest, this.toolClasses, results, rateLimiter);

        return results.toString();
    }
}

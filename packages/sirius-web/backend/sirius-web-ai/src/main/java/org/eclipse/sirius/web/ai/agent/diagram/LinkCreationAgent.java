package org.eclipse.sirius.web.ai.agent.diagram;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.dto.AgentResult;
import org.eclipse.sirius.web.ai.service.ToolCallService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.creation.LinkCreationTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.DIAGRAM_MODEL;

@Service
public class LinkCreationAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(LinkCreationAgent.class);

    private final ChatLanguageModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private IInput input;

    public LinkCreationAgent(LinkCreationTools linkCreationTools) {
        this.model = AiModelsConfiguration.buildLanguageModel(DIAGRAM_MODEL);
        this.toolClasses.add(linkCreationTools);
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

    @Tool("Links two objects together. Do not mention other properties here.")
    public String linkObjects(@P("Explain what kind of links is preferred, if possible.") String prompt, @P("The source object id, the id is in a format similar to \"AbcdEF+GhijKLM1NOpqrS==\".") String sourceObjectId, @P("The target object id, the id is in a format similar to \"AbcdEF+GhijKLM1NOpqrS==\".") String targetObjectId) throws UnsupportedOperationException, InterruptedException {
        var rateLimiter = AiModelsConfiguration.getRateLimiter(this.model);
        var previousMessages = new ArrayList<ChatMessage>();
        var specifications = new ArrayList<>(initializeSpecifications(List.of(), this.input, this.toolClasses));
        this.setToolsInput();

        previousMessages.add(new SystemMessage("""
            You are an assistant for Diagram Object Linking.
            Do not write any text, just call the correct tools to link two diagram objects given in the user's request .
            Do not hallucinate. When it is possible to link objects, link them, if possible with the user preferred link.
            """
        ));

        previousMessages.add(new UserMessage("Here is the source diagram object id: " + sourceObjectId + " and here is the target object id: " + targetObjectId + ". " + prompt));

        var results = new ArrayList<AgentResult>();
        ToolCallService.computeToolCalls(logger, this.model, previousMessages, this.toolClasses, specifications, results, rateLimiter);

        return results.toString();
    }
}

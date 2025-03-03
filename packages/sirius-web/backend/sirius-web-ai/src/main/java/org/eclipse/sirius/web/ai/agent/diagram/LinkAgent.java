package org.eclipse.sirius.web.ai.agent.diagram;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.service.ToolCallService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.creation.LinkCreationTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LinkAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(LinkAgent.class);

    private final ChatLanguageModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private IInput input;

    public LinkAgent(LinkCreationTools linkCreationTools) {
        this.model = AiModelsConfiguration.buildLanguageModel(AiModelsConfiguration.ModelType.DIAGRAM_MODEL);
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
    public String linkObjects(@P("Explain what kind of links is preferred, if possible.") String prompt, @P("The source object id.") String sourceObjectId, @P("The target object id.") String targetObjectId) throws UnsupportedOperationException {
        List<ChatMessage> previousMessages = new ArrayList<>();
        List<ToolSpecification> specifications = new ArrayList<>();

        initializeSpecifications(List.of(), this.input, this.toolClasses, specifications);
        this.setToolsInput();

        previousMessages.add(new SystemMessage("""
            You are an assistant for Diagram Object Linking.
            Do not write any text, just call the correct tools to link two diagram objects given in the user's request .
            Do not hallucinate.
            """
        ));

        previousMessages.add(new UserMessage("Here is the source diagram object id: " + sourceObjectId + " and here is the target object id: " + targetObjectId + ". " + prompt));

        ToolCallService.computeToolCalls(logger, this.model, previousMessages, this.toolClasses, specifications);
        // ToolCallService.computeToolCalls(this.model, previousMessages, this.toolClasses, specifications, List.of(this.editionAgent));

        previousMessages.add(new UserMessage("Now, summarize the important information you created, structured as \"Link LinkType created with id LinkId\", or explain why your task was not concluded."));

        return model.generate(previousMessages).content().text();
    }
}

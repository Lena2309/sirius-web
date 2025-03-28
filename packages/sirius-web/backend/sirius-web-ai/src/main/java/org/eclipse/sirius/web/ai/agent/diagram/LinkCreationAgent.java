package org.eclipse.sirius.web.ai.agent.diagram;

import org.eclipse.sirius.web.ai.agent.diagram.edition.LinkEditionAgent;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.service.ToolCallService;
import org.eclipse.sirius.web.ai.tool.creation.LinkCreationTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.DIAGRAM;

@Service
public class LinkCreationAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(LinkCreationAgent.class);

    private final ThreadPoolTaskExecutor executor;

    private final Optional<ChatModel> model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private final LinkCreationTools linkCreationTools;

    private final LinkEditionAgent linkEditionAgent;

    private IInput input;

    public LinkCreationAgent(LinkCreationTools linkCreationTools, LinkEditionAgent linkEditionAgent,
                             ThreadPoolTaskExecutor executor) {
        this.executor = executor;
        this.model = AiModelsConfiguration.builder()
                .type(DIAGRAM)
                .build();
        this.linkEditionAgent = linkEditionAgent;
        this.toolClasses.add(linkCreationTools);
        this.linkCreationTools = linkCreationTools;
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

    @Tool(description ="Links two objects together.")
    public String linkObjects(@ToolParam(description ="Explain what should be linked and why. Mention special properties if necessary.") String orchestratorPrompt, @ToolParam(description ="The source object id.") String sourceObjectId, @ToolParam(description = "The target object id.") String targetObjectId) throws UnsupportedOperationException {
        logger.info("Linking objects {} and {}: {}", sourceObjectId, targetObjectId, orchestratorPrompt);
        this.linkCreationTools.clearLinkIds();
        initializeSpecifications(List.of(this.linkEditionAgent), this.input);
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Diagram Object Linking.
            Do not write any text, just call the correct tools to link two diagram objects given in the user's request .
            When it is possible to link objects, link them, if possible with the user preferred link.
            If the preferred link is not available, you should still link the objects in the best way possible.
            Modify links properties as you create them, be mindfull to use the correct ids for each link. Each link has different ids.
            You have to respect the tool signature. You must call tools.
            Do not hallucinate.
            """
        );

        var prompt = new Prompt(systemMessage, new UserMessage("Here is the source diagram object id: " + sourceObjectId + " and here is the target object id: " + targetObjectId + ". " + orchestratorPrompt));

        assert this.model.isPresent();
        new ToolCallService().computeToolCalls(logger, this.model.get(), prompt, this.executor, this.linkCreationTools, this.linkEditionAgent);

        logger.info(this.linkCreationTools.getLinkIds().toString());
        return this.linkCreationTools.getLinkIds().toString();
    }
}
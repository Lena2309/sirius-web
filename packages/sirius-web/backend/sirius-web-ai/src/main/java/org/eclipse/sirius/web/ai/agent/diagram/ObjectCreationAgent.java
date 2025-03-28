package org.eclipse.sirius.web.ai.agent.diagram;

import org.eclipse.sirius.web.ai.agent.diagram.edition.ObjectEditionAgent;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.service.ToolCallService;
import org.eclipse.sirius.web.ai.tool.creation.ObjectCreationTools;
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
public class ObjectCreationAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ObjectCreationAgent.class);

    private final ThreadPoolTaskExecutor executor;

    private final Optional<ChatModel> model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private final ObjectEditionAgent objectEditionAgent;

    private final ObjectCreationTools objectCreationTools;

    private IInput input;

    public ObjectCreationAgent(ObjectCreationTools objectCreationTools, ObjectEditionAgent objectEditionAgent,
                               ThreadPoolTaskExecutor executor) {
        this.executor = executor;
        this.model = AiModelsConfiguration.builder()
                .type(DIAGRAM)
                .build();
        this.objectEditionAgent = objectEditionAgent;
        this.toolClasses.add(objectCreationTools);
        this.objectCreationTools = objectCreationTools;
    }

    @Override
    public void setInput(IInput input) {
        this.input = input;
    }

    @Override
    public void setToolsInput() {
        for (AiTool toolClass : this.toolClasses) {
            toolClass.setInput(this.input);
        }
    }

    @Tool(description = "Creates one root object and its children. Can edit them and name them. Does not link them.")
    public String createObject(@ToolParam(description = "Explain what object to create and the children it should contain, mention names and special properties and explain what they aim to represent. Do not mention links here.") String orchestratorPrompt) {
        logger.info("Creating a new object: {}", orchestratorPrompt);
        this.objectCreationTools.clearObjectIds();
        initializeSpecifications(List.of(this.objectEditionAgent), this.input);
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Object Diagram Generation.
            Do not write any text, just call the correct tools to create the correct diagram elements listed in the user's request.
            Modify objects properties as you create them, be mindfull to use the correct ids for each object. Each object has different ids.
            You have to respect the tool signature. You must call tools.
            Do not hallucinate.
            """
        );

        return callModel(orchestratorPrompt, systemMessage);
    }

    @Tool(description = "Creates one or multiple children in an already existing object. Can edit them and name them. Does not link them. Useless if the parent does not already exists.")
    public String createChild(@ToolParam(description = "Explain what child to create within an already existing object and the children it should contain, mention names and special properties and explain they aim to represent. Do not mention links here.") String orchestratorPrompt, @ToolParam(description = "The parent id.") String parentId) {
        logger.info("Creating a child for {}: {}", parentId, orchestratorPrompt);
        this.objectCreationTools.clearObjectIds();
        initializeSpecifications(List.of(this.objectEditionAgent), this.input);
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Diagram Generation.
            Do not write any text, just call the correct tools to create the correct diagram elements listed in the user's request, do not hallucinate.
            Your purpose is to create children for the object:
            """+parentId
        );

        return callModel(orchestratorPrompt, systemMessage);
    }

    private String callModel(@ToolParam(description = "Explain what child to create within an already existing object and the children it should contain, mention names and special properties and explain they aim to represent. Do not mention links here.") String orchestratorPrompt, SystemMessage systemMessage) {
        var prompt = new Prompt(systemMessage, new UserMessage(orchestratorPrompt));

        assert this.model.isPresent();
        new ToolCallService().computeToolCalls(logger, this.model.get(), prompt, this.executor, this.objectCreationTools, this.objectEditionAgent);

        logger.info(this.objectCreationTools.getObjectIds().toString());
        return this.objectCreationTools.getObjectIds().toString();
    }
}
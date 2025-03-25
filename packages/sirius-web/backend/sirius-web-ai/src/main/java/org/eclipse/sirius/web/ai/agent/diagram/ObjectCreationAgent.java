package org.eclipse.sirius.web.ai.agent.diagram;

import org.eclipse.sirius.web.ai.agent.diagram.edition.ObjectEditionAgent;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.creation.ObjectCreationTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.DIAGRAM;

@Service
public class ObjectCreationAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ObjectCreationAgent.class);

    private final ChatModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private final ObjectEditionAgent objectEditionAgent;

    private final ObjectCreationTools objectCreationTools;

    private final ThreadPoolTaskExecutor taskExecutor;

    private IInput input;

    public ObjectCreationAgent(ObjectCreationTools objectCreationTools, ObjectEditionAgent objectEditionAgent,
                               @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.model = AiModelsConfiguration.buildChatModel(DIAGRAM).get();
        this.objectEditionAgent = objectEditionAgent;
        this.taskExecutor = taskExecutor;
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

    @Tool(description = "Creates one root object and its potential children. Can edit them and name them. Does not link them.")
    public String createObject(@ToolParam(description = "Explain what object to create and the children it may contain, mention names and special properties if necessary. Do not mention links here.") String orchestratorPrompt) {
        this.objectCreationTools.clearObjectIds();
        initializeSpecifications(List.of(this.objectEditionAgent), this.input);
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Object Diagram Generation.
            Do not write any text, just call the correct tools to create the correct diagram elements listed in the user's request.
            You have to respect the tool signature.
            Do not hallucinate.
            """
        );

        var chatClient = ChatClient.builder(this.model)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();

        var prompt = new Prompt(systemMessage, new UserMessage(orchestratorPrompt));

        chatClient.prompt(prompt).tools(objectEditionAgent, objectCreationTools).call();
        return this.objectCreationTools.getObjectIds().toString();
    }

    @Tool(description = "Creates one or multiple children in an object. Can edit them and name them. Does not link them. Useless if the parent does not already exists.")
    public String createChild(@ToolParam(description = "Explain what child to create within an already existing object and the children it may contain, mention names and special properties if necessary. Do not mention links here.") String orchestratorPrompt, @ToolParam(description = "The parent id.") String parentId) {
        this.objectCreationTools.clearObjectIds();
        initializeSpecifications(List.of(this.objectEditionAgent), this.input);
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Diagram Generation.
            Do not write any text, just call the correct tools to create the correct diagram elements listed in the user's request, do not hallucinate.
            Your purpose is to create children for the object:
            """+parentId
        );

        var chatClient = ChatClient.builder(this.model)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();

        var prompt = new Prompt(systemMessage, new UserMessage(orchestratorPrompt));

        chatClient.prompt(prompt).tools(objectEditionAgent, objectCreationTools).call();
        return this.objectCreationTools.getObjectIds().toString();
    }
}
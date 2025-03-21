package org.eclipse.sirius.web.ai.agent.diagram;

import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.deletion.LinkDeletionTools;
import org.eclipse.sirius.web.ai.tool.deletion.ObjectDeletionTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.DIAGRAM;

@Service
public class DeletionAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(DeletionAgent.class);

    private final ChatModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private final ObjectDeletionTools objectDeletionTools;

    private final LinkDeletionTools linkDeletionTools;

    private IInput input;

    public DeletionAgent(ObjectDeletionTools objectDeletionTools, LinkDeletionTools linkDeletionTools) {
        this.model = AiModelsConfiguration.buildChatModel(DIAGRAM).get();
        this.toolClasses.add(objectDeletionTools);
        this.toolClasses.add(linkDeletionTools);
        this.objectDeletionTools = objectDeletionTools;
        this.linkDeletionTools = linkDeletionTools;
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

    @Tool(description = "Delete a diagram element, can be an object or a link.")
    public String deleteElement(@ToolParam(description = "Explain what to delete.") String orchestratorPrompt, @ToolParam(description = "The element id to delete, the id is in a format similar to \"AbcdEF+GhijKLM1NOpqrS==\".") String elementId) {
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Diagram Element Deletion.
            Do not write any text, just call the correct tools to edit the correct diagram element given in the user's request.
            Do not hallucinate.
            """
        );

        var chatClient = ChatClient.builder(this.model)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();

        var prompt = new Prompt(systemMessage, new UserMessage(orchestratorPrompt));

        return chatClient.prompt(prompt).tools(objectDeletionTools, linkDeletionTools).call().content();
    }
}

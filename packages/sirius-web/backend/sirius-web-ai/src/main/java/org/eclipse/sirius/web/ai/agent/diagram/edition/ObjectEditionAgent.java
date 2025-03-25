package org.eclipse.sirius.web.ai.agent.diagram.edition;

import org.eclipse.sirius.web.ai.agent.diagram.DiagramAgent;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.edition.ObjectEditionTools;
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

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.EDITION;

@Service
public class ObjectEditionAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ObjectEditionAgent.class);

    private final ChatModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private final ObjectEditionTools objectEditionTools;

    private IInput input;

    public ObjectEditionAgent(ObjectEditionTools objectEditionTools) {
        this.model = AiModelsConfiguration.buildChatModel(EDITION).get();
        this.toolClasses.add(objectEditionTools);
        this.objectEditionTools = objectEditionTools;
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

    @Tool(description = "Edit an object's properties.")
    public void editObjectProperties(@ToolParam(description = "Explain what properties to modify with their new values.") String orchestratorPrompt, @ToolParam(description = "The object id to edit.") String objectId) {
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Diagram Object Edition.
            Do not write any text, just call the correct tools to edit the correct diagram element given in the user's request.
            Before trying to edit a property, you have to verify that it exists in the first place, then choose the most appropriate to edit.
            Do not hallucinate, do not invent properties and pay attention to their types.
            """
        );

        var chatClient = ChatClient.builder(this.model)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();

        var prompt = new Prompt(systemMessage, new UserMessage("Here is the object to edit: " + objectId + ". " + orchestratorPrompt));

        chatClient.prompt(prompt).tools(objectEditionTools).call();
    }
}
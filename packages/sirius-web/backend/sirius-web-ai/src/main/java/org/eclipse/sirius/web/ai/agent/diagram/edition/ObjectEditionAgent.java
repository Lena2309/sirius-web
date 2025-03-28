package org.eclipse.sirius.web.ai.agent.diagram.edition;

import org.eclipse.sirius.web.ai.agent.diagram.DiagramAgent;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.edition.ObjectEditionTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.web.ai.tool.service.ToolCallService;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.EDITION;

@Service
public class ObjectEditionAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ObjectEditionAgent.class);

    private final ThreadPoolTaskExecutor executor;

    private final Optional<ChatModel> model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private final ObjectEditionTools objectEditionTools;

    private IInput input;

    public ObjectEditionAgent(ObjectEditionTools objectEditionTools, ThreadPoolTaskExecutor executor) {
        this.executor = executor;
        this.model = AiModelsConfiguration.builder()
                .type(EDITION)
                .build();
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
    public void editObjectProperties(@ToolParam(description = "Explain what properties to modify with their new values. Explain why this change is necessary and it represents.") String instructionPrompt, @ToolParam(description = "The object id to edit.") String objectId) {
        logger.info("Edit the properties of {} to \"{}\"", objectId, instructionPrompt);
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Diagram Object Edition.
            Do not write any text, just call the correct tools to edit the correct diagram element given in the user's request.
            Before trying to edit a property, you have to verify that it exists in the first place, then choose the most appropriate to edit.
            If the user wants to edit a property that does not exist, find the closest one that could match and edit it accordingly.
            You can take liberties but do not invent properties.
            Do not hallucinate.
            """
        );

        var prompt = new Prompt(systemMessage, new UserMessage("Here is the object to edit: " + objectId + ". " + instructionPrompt));

        assert this.model.isPresent();
        new ToolCallService().computeToolCalls(logger, this.model.get(), prompt, this.executor, this.objectEditionTools);
    }
}
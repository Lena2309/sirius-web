package org.eclipse.sirius.web.ai.agent.diagram.edition;


import org.eclipse.sirius.web.ai.agent.diagram.DiagramAgent;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.reason.PromptInterpreter;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.edition.LinkEditionTools;
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
public class LinkEditionAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(LinkEditionAgent.class);

    private final ChatModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private final LinkEditionTools linkEditionTools;
    private final PromptInterpreter promptInterpreter;

    private IInput input;

    public LinkEditionAgent(LinkEditionTools linkEditionTools, PromptInterpreter promptInterpreter) {
        this.model = AiModelsConfiguration.buildChatModel(EDITION).get();
        this.toolClasses.add(linkEditionTools);
        this.linkEditionTools = linkEditionTools;
        this.promptInterpreter = promptInterpreter;
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

    @Tool(description = "Edit a link's properties.")
    public void editLinkProperties(@ToolParam(description = "Explain what properties to modify with their new values and why this change is necessary and what it represents.") String instructionPrompt, @ToolParam(description = "The link id to edit.") String linkId) {
        logger.info(instructionPrompt);
        initializeSpecifications(List.of(), this.input);
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Diagram Link Edition.
            Do not write any text, just call the correct tools to edit the correct diagram element given in the user's request.
            Before trying to edit a property, you have to verify that it exists in the first place, then choose the most appropriate to edit.
            If the user wants to edit a property that does not exist, find the closest one that could match and edit it accordingly.
            You can take liberties but do not invent properties.
            Do not hallucinate.
            """
        );

        var chatClient = ChatClient.builder(this.model)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();

        var prompt = new Prompt(systemMessage, new UserMessage("Here is the link to edit: " + linkId + ". " + instructionPrompt));

        chatClient.prompt(prompt).tools(linkEditionTools).call().content();
    }
}
package org.eclipse.sirius.ai.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.eclipse.sirius.ai.tool.creation.LinkCreationTools;
import org.eclipse.sirius.ai.tool.creation.ObjectCreationTools;
import org.eclipse.sirius.ai.tool.AiTools;
import org.eclipse.sirius.ai.tool.getter.LinkGetterTools;
import org.eclipse.sirius.ai.tool.getter.ObjectGetterTools;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CreationAgent extends ToolAgent {
    private final EditionAgent editionAgent;

    public CreationAgent(ChatLanguageModel model, ObjectGetterTools objectGetterTools, ObjectCreationTools objectCreationTools,
                         LinkGetterTools linkGetterTools, LinkCreationTools linkCreationTools,
                         EditionAgent editionAgent) {
        super(model, objectGetterTools, linkGetterTools);
        this.toolClasses.add(objectCreationTools);
        this.toolClasses.add(linkCreationTools);
        setLogger(this);
        this.editionAgent = editionAgent;
    }

    public void generate(String prompt) {
        List<ChatMessage> previousMessages = new ArrayList<>();
        List<ToolSpecification> specifications = new ArrayList<>();

        for (AiTools toolClass : toolClasses) {
            specifications.addAll(ToolSpecifications.toolSpecificationsFrom(toolClass));
        }
        //specifications.addAll(ToolSpecifications.toolSpecificationsFrom(editionAgent));

        previousMessages.add(new SystemMessage("""
            You are an assistant for Diagram Generation.
            Do not write any text, just call the correct tools to create the correct diagram elements listed in the user's request, do not hallucinate.
            """
                // After creating an element, you have the possibility to call another agent to edit its properties.
                // Just explain what would you like to edit, if it's possible, and to what values.
        ));
        previousMessages.add(new UserMessage(prompt));

        computeToolCalls(previousMessages, specifications);
    }
}

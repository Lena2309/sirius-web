package org.eclipse.sirius.web.ai.agent.diagram;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.eclipse.sirius.web.ai.service.ToolCallService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.deletion.LinkDeletionTools;
import org.eclipse.sirius.web.ai.tool.deletion.ObjectDeletionTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;

@Service
public class DeletionAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(DeletionAgent.class);

    private final ChatLanguageModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private IInput input;

    public DeletionAgent(ObjectDeletionTools objectDeletionTools, LinkDeletionTools linkDeletionTools) {
        this.model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_4_O)
                .temperature(0.2)
                .build();
        this.toolClasses.add(objectDeletionTools);
        this.toolClasses.add(linkDeletionTools);
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

    @Tool("Delete a diagram element, can be an object or a link.")
    public void deleteElement(@P("Explain what to delete.") String prompt, @P("The element id to delete.") String elementId) {
        List<ChatMessage> previousMessages = new ArrayList<>();
        List<ToolSpecification> specifications = new ArrayList<>();

        initializeSpecifications(List.of(), this.input, this.toolClasses, specifications);
        this.setToolsInput();

        previousMessages.add(new SystemMessage("""
            You are an assistant for Diagram Element Deletion.
            Do not write any text, just call the correct tools to edit the correct diagram element given in the user's request.
            Do not hallucinate.
            """
        ));

        previousMessages.add(new UserMessage("Here is the diagram element id to delete: " + elementId + ". " + prompt));

        ToolCallService.computeToolCalls(logger, this.model, previousMessages, this.toolClasses, specifications);
    }
}

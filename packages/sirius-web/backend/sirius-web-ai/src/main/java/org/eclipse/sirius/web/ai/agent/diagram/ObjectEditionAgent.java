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
import org.eclipse.sirius.web.ai.tool.edition.ObjectEditionTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;

@Service
public class ObjectEditionAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ObjectEditionAgent.class);

    private final ChatLanguageModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private IInput input;

    public ObjectEditionAgent(ObjectEditionTools objectEditionTools) {
        this.model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_4_O)
                .temperature(0.5)
                .build();
        this.toolClasses.add(objectEditionTools);
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

    @Tool("Edit an object's properties.")
    public String editObject(@P("Explain what properties to modify with their new values.") String prompt, @P("The object id to edit.") String objectId) {
        List<ChatMessage> previousMessages = new ArrayList<>();
        List<ToolSpecification> specifications = new ArrayList<>();

        initializeSpecifications(List.of(), this.input, this.toolClasses, specifications);
        this.setToolsInput();

        previousMessages.add(new SystemMessage("""
            You are an assistant for Diagram Object Edition.
            Do not write any text, just call the correct tools to edit the correct diagram element given in the user's request.
            Do not hallucinate.
            """
        ));

        previousMessages.add(new UserMessage("Here is the object to edit: " + objectId + ". " + prompt));

        ToolCallService.computeToolCalls(logger, this.model, previousMessages, this.toolClasses, specifications);
        previousMessages.add(new UserMessage("Now, summarize the important information you created, structured as \"PropertyLabel of ObjectId set to PropertyNewValue \""));

        return model.generate(previousMessages).content().text();
    }
}

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
import org.eclipse.sirius.web.ai.tool.creation.ObjectCreationTools;
import org.eclipse.sirius.web.ai.tool.getter.ObjectGetterTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

@Service
public class ObjectAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ObjectAgent.class);

    private final ChatLanguageModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private IInput input;

    public ObjectAgent(ObjectGetterTools objectGetterTools, ObjectCreationTools objectCreationTools) {
        this.model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_4_O)
                .temperature(0.3)
                .build();
        this.toolClasses.add(objectGetterTools);
        this.toolClasses.add(objectCreationTools);
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

    @Tool("Creates one root object and its potential children. Does not edit them.")
    public String createObject(@P("Explain what object to create and the children it may contain. Do not mention links and properties here.") String prompt) {
        List<ChatMessage> previousMessages = new ArrayList<>();
        List<ToolSpecification> specifications = new ArrayList<>();

        initializeSpecifications(List.of(), this.input, this.toolClasses, specifications);
        this.setToolsInput();

        previousMessages.add(new SystemMessage("""
            You are an assistant for Object Diagram Generation.
            Do not write any text, just call the correct tools to create the correct diagram elements listed in the user's request.
            Do not hallucinate.
            """
        ));
        previousMessages.add(new UserMessage(prompt));

        ToolCallService.computeToolCalls(logger, this.model, previousMessages, this.toolClasses, specifications);
        previousMessages.add(new UserMessage("Now, summarize the important information you created, structured as \"ObjectType created with id ObjectId\""));

        return model.generate(previousMessages).content().text();
    }

    @Tool("Creates one or multiple children in an object. Does not edit them. Useless if the parent does not already exists.")
    public String createChild(@P("Explain what child to create within an already existing object and the children it may contain. Do not mention links and properties here.") String prompt, @P("The parent id.") String parentId) {
        List<ChatMessage> previousMessages = new ArrayList<>();
        List<ToolSpecification> specifications = new ArrayList<>();

        initializeSpecifications(List.of(), this.input, this.toolClasses, specifications);
        this.setToolsInput();

        previousMessages.add(new SystemMessage("""
            You are an assistant for Diagram Generation.
            Do not write any text, just call the correct tools to create the correct diagram elements listed in the user's request, do not hallucinate.
            Your purpose is to create children for the object:
            """+parentId
        ));
        previousMessages.add(new UserMessage(prompt));

        ToolCallService.computeToolCalls(logger, this.model, previousMessages, this.toolClasses, specifications);
        previousMessages.add(new UserMessage("Now, summarize the object you created, you must differentiate root objects, structured as \"ObjectType created with id ObjectId\", from children, structured as \"ChildrenType child of ParentType created with id ChildId\""));

        return model.generate(previousMessages).content().text();
    }
}

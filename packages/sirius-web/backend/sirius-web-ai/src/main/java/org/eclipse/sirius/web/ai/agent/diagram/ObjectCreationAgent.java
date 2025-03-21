package org.eclipse.sirius.web.ai.agent.diagram;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.eclipse.sirius.web.ai.agent.diagram.edition.ObjectEditionAgent;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.configuration.BlockingRateLimiter;
import org.eclipse.sirius.web.ai.dto.AgentResult;
import org.eclipse.sirius.web.ai.service.ToolCallService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.tool.creation.ObjectCreationTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.DIAGRAM;

@Service
public class ObjectCreationAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ObjectCreationAgent.class);

    private final ChatLanguageModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private final ObjectEditionAgent objectEditionAgent;

    private final ThreadPoolTaskExecutor taskExecutor;

    private IInput input;

    public ObjectCreationAgent(ObjectCreationTools objectCreationTools, ObjectEditionAgent objectEditionAgent,
                               @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.model = AiModelsConfiguration.buildChatModel(DIAGRAM).get();
        this.objectEditionAgent = objectEditionAgent;
        this.taskExecutor = taskExecutor;
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

    @Tool("Creates one root object and its potential children. Can edit them and name them. Does not link them.")
    public String createObject(@P("Explain what object to create and the children it may contain, mention names and special properties if necessary. Do not mention links here.") String prompt) {
        var rateLimiter = AiModelsConfiguration.getRateLimiter(this.model);
        var specifications = new ArrayList<>(initializeSpecifications(List.of(this.objectEditionAgent), this.input, this.toolClasses));
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Object Diagram Generation.
            Do not write any text, just call the correct tools to create the correct diagram elements listed in the user's request.
            You have to respect the tool signature.
            Do not hallucinate.
            """
        );

        return callTools(prompt, rateLimiter, specifications, systemMessage);
    }

    @Tool("Creates one or multiple children in an object. Can edit them and name them. Does not link them. Useless if the parent does not already exists.")
    public String createChild(@P("Explain what child to create within an already existing object and the children it may contain, mention names and special properties if necessary. Do not mention links here.") String prompt, @P("The parent id.") String parentId) {
        var rateLimiter = AiModelsConfiguration.getRateLimiter(this.model);
        var specifications = new ArrayList<>(initializeSpecifications(List.of(this.objectEditionAgent), this.input, this.toolClasses));
        this.setToolsInput();

        var systemMessage = new SystemMessage("""
            You are an assistant for Diagram Generation.
            Do not write any text, just call the correct tools to create the correct diagram elements listed in the user's request, do not hallucinate.
            Your purpose is to create children for the object:
            """+parentId
        );

        return callTools(prompt, rateLimiter, specifications, systemMessage);
    }

    private String callTools(String prompt, BlockingRateLimiter rateLimiter, ArrayList<ToolSpecification> specifications, SystemMessage systemMessage) {
        var chatRequest = ChatRequest.builder()
                .messages(List.of(systemMessage, new UserMessage(prompt)))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(specifications)
                        .build())
                .build();

        var results = new ArrayList<AgentResult>();
        //ToolCallService.computeToolCalls(logger, this.model, chatRequest, this.toolClasses, results, rateLimiter);
        ToolCallService.computeToolCalls(logger, this.model, chatRequest, List.of(this.objectEditionAgent), this.toolClasses, results, this.taskExecutor, rateLimiter);

        return results.toString();
    }
}

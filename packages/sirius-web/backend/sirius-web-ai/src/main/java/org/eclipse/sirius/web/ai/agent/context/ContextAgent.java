package org.eclipse.sirius.web.ai.agent.context;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.eclipse.sirius.web.ai.agent.Agent;
import org.eclipse.sirius.web.ai.agent.diagram.DiagramAgent;
import org.eclipse.sirius.web.ai.agent.diagram.ObjectEditionAgent;
import org.eclipse.sirius.web.ai.agent.diagram.LinkAgent;
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

@Service
public class ContextAgent implements DiagramAgent {
    private static final Logger logger = LoggerFactory.getLogger(ContextAgent.class);

    private final ChatLanguageModel model;

    private final List<AiTool> toolClasses = new ArrayList<>();

    private final List<Agent> agents = new ArrayList<>();

    private final ThreadPoolTaskExecutor taskExecutor;

    private IInput input;

    public ContextAgent(ChatLanguageModel model, ObjectGetterTools objectGetterTools, ObjectCreationTools objectCreationTools,
                        ObjectEditionAgent objectEditionAgent, LinkAgent linkAgent, @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.model = model;
        this.toolClasses.add(objectGetterTools);
        this.toolClasses.add(objectCreationTools);
        this.agents.add(objectEditionAgent);
        this.agents.add(linkAgent);
        this.taskExecutor = taskExecutor;
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

    @Tool("Creates a diagram with multiple objects, edits and links them too.")
    public void createDiagram(@P("Explain what to create, with links and special properties.") String prompt) {
        List<ChatMessage> previousMessages = new ArrayList<>();
        List<ToolSpecification> specifications = new ArrayList<>();

        initializeSpecifications(this.agents, this.input, this.toolClasses, specifications);
        this.setToolsInput();


        previousMessages.add(new SystemMessage("""
            You are an assistant for Diagram Generation.
            Do not write any text, just call the correct tools to create the correct diagram elements listed in the user's request, do not hallucinate.
            After creating an element, you have the possibility to call another agent to edit its properties.
            Just explain what would you like to edit, if it's possible, and to what values.
            """
        ));
        previousMessages.add(new UserMessage(prompt));

        ToolCallService.computeToolCalls(logger, this.model, previousMessages, this.toolClasses, specifications, this.agents, this.taskExecutor);
    }
}

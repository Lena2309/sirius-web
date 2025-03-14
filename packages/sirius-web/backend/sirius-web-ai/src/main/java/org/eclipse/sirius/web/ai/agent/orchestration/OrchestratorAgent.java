package org.eclipse.sirius.web.ai.agent.orchestration;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.agent.Agent;
import org.eclipse.sirius.web.ai.agent.diagram.*;
import org.eclipse.sirius.web.ai.reason.PromptInterpreter;
import org.eclipse.sirius.web.ai.dto.AiRequestInput;
import org.eclipse.sirius.web.ai.service.ToolCallService;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.ORCHESTRATION;

@Service
public class OrchestratorAgent implements Agent {
    private final Logger logger = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final ChatLanguageModel model;

    private final PromptInterpreter promptInterpreter;

    private final DeletionAgent deletionAgent;

    private final ObjectCreationAgent objectCreationAgent;

    private final LinkCreationAgent linkCreationAgent;

    private final ThreadPoolTaskExecutor taskExecutor;

    public OrchestratorAgent(PromptInterpreter promptInterpreter, DeletionAgent deletionAgent,
                             ObjectCreationAgent objectCreationAgent, LinkCreationAgent linkCreationAgent,
                             @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.model = AiModelsConfiguration.buildChatModel(ORCHESTRATION).get();
        this.promptInterpreter = Objects.requireNonNull(promptInterpreter);
        this.deletionAgent = Objects.requireNonNull(deletionAgent);
        this.objectCreationAgent = Objects.requireNonNull(objectCreationAgent);
        this.linkCreationAgent = Objects.requireNonNull(linkCreationAgent);
        this.taskExecutor = Objects.requireNonNull(taskExecutor);
    }

    public void compute(IInput input) {
        if (input instanceof AiRequestInput aiRequestInput) {
            var rateLimiter = AiModelsConfiguration.getRateLimiter(this.model);
            var specifications = new ArrayList<>(initializeSpecifications(List.of(this.deletionAgent, this.objectCreationAgent, this.linkCreationAgent), aiRequestInput, List.of()));
            this.promptInterpreter.setInput(aiRequestInput);

            var systemMessage = new SystemMessage("""
                    You are an orchestrating agent for Diagram Generation.
                    From the user prompt and the list of concepts computed from it, call the correct tools to create or edit a diagram.
                    You are encouraged to call multiple tools at the same time, since they are parallelized.
                    Start with the creation and deletion of objects. Once done, continue with the linking and edition of objects.
                    Do not hallucinate.
                    
                    Call the tools in batches:
                        1. Object Creations and Deletions
                        2. Link Creations and Deletions
                        3. Object and Link Editions
                    The batches must be separate, but you can make multiple tool calls at the time per batches.
                    """);

            var concepts = this.promptInterpreter.think(aiRequestInput.prompt());

            var chatRequest = ChatRequest.builder()
                    .messages(List.of(systemMessage, new UserMessage(concepts)))
                    .parameters(ChatRequestParameters.builder()
                            .toolSpecifications(specifications)
                            .build())
                    .build();

            ToolCallService.computeToolCalls(logger, this.model, chatRequest, List.of(this.deletionAgent, this.objectCreationAgent, this.linkCreationAgent), List.of(), List.of(), this.taskExecutor, rateLimiter);

            AiModelsConfiguration.executionDone();
        }
    }
}

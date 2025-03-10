package org.eclipse.sirius.web.ai.agent.routing;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.agent.Agent;
import org.eclipse.sirius.web.ai.agent.diagram.*;
import org.eclipse.sirius.web.ai.agent.reason.ReasonAgent;
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

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.ORCHESTRATION_MODEL;

@Service
public class OrchestratorAgent implements Agent {
    private final Logger logger = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final ChatLanguageModel model;

    private final ReasonAgent reasonAgent;

    private final DeletionAgent deletionAgent;

    private final ObjectCreationAgent objectCreationAgent;

    private final ObjectEditionAgent objectEditionAgent;

    private final LinkCreationAgent linkCreationAgent;

    private final LinkEditionAgent linkEditionAgent;

    private final ThreadPoolTaskExecutor taskExecutor;

    public OrchestratorAgent(ReasonAgent reasonAgent, DeletionAgent deletionAgent,
                             ObjectCreationAgent objectCreationAgent, ObjectEditionAgent objectEditionAgent,
                             LinkCreationAgent linkCreationAgent, LinkEditionAgent linkEditionAgent,
                             @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.model = AiModelsConfiguration.buildLanguageModel(ORCHESTRATION_MODEL);
        this.reasonAgent = Objects.requireNonNull(reasonAgent);
        this.deletionAgent = Objects.requireNonNull(deletionAgent);
        this.objectCreationAgent = Objects.requireNonNull(objectCreationAgent);
        this.objectEditionAgent = Objects.requireNonNull(objectEditionAgent);
        this.linkCreationAgent = Objects.requireNonNull(linkCreationAgent);
        this.linkEditionAgent = Objects.requireNonNull(linkEditionAgent);
        this.taskExecutor = Objects.requireNonNull(taskExecutor);
    }

    public void compute(IInput input) {
        if (input instanceof AiRequestInput aiRequestInput) {
            var rateLimiter = AiModelsConfiguration.getRateLimiter(this.model);
            var previousMessages = new ArrayList<ChatMessage>();
            var specifications = new ArrayList<>(initializeSpecifications(List.of(this.objectCreationAgent, this.deletionAgent, this.objectEditionAgent, this.linkCreationAgent), aiRequestInput, List.of()));
            this.reasonAgent.setInput(aiRequestInput);

            previousMessages.add(new SystemMessage("""
                    You are an orchestrating agent for Diagram Generation.
                    From the user prompt and the list of concepts computed from it, call the correct tools to create or edit a diagram.
                    You are encouraged to call multiple tools at the same time, since they are parallelized.
                    Start with the creation and deletion of objects. Once done, continue with the linking and edition of objects.
                    Do not hallucinate.
                    
                    Call the tools in batches:
                        1. Object Creations and Deletions
                        2. Link Creations and Deletions
                        3. Object and Link Editions
                    The batches must be separate.
                    """));

            previousMessages.add(new UserMessage(aiRequestInput.prompt()));

            var concepts = this.reasonAgent.think(aiRequestInput.prompt());
            previousMessages.add(new UserMessage("The listed concepts computed with the original user prompt: "+concepts));

            ToolCallService.computeToolCalls(logger, this.model, previousMessages, specifications, List.of( this.deletionAgent, this.objectCreationAgent, this.linkCreationAgent, this.objectEditionAgent, this.linkEditionAgent), this.taskExecutor, rateLimiter);

            /*
            previousMessages.add(new UserMessage("First, create objects accordingly and/or modify already existing elements."));
            ToolCallService.computeToolCalls(logger, this.model, previousMessages, specifications, List.of(this.objectCreationAgent, this.deletionAgent, this.objectEditionAgent , this.linkEditionAgent), this.taskExecutor, rateLimiter);

            previousMessages.add(new UserMessage("Now, link objects together accordingly."));
            ToolCallService.computeToolCalls(logger, this.model, previousMessages, specifications, List.of(this.linkCreationAgent, this.objectEditionAgent , this.linkEditionAgent), this.taskExecutor, rateLimiter);

            previousMessages.add(new UserMessage("If the previous tools (especially the linking tool) did not work, try something else. If it did work, do not call for tools."));
            ToolCallService.computeToolCalls(logger, this.model, previousMessages, specifications, List.of(this.objectCreationAgent, this.deletionAgent, this.linkCreationAgent, this.objectEditionAgent , this.linkEditionAgent), this.taskExecutor, rateLimiter);

            previousMessages.add(new UserMessage("Now, continue with the edition of objects and links accordingly."));
            ToolCallService.computeToolCalls(logger, this.model, previousMessages, specifications, List.of(this.objectEditionAgent , this.linkEditionAgent), this.taskExecutor, rateLimiter);

             */

            AiModelsConfiguration.executionDone();
        }
    }
}

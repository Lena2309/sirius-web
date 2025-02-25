package org.eclipse.sirius.web.ai.agent.routing;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
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

@Service
public class RoutingAgent implements Agent {
    private final Logger logger = LoggerFactory.getLogger(RoutingAgent.class);

    private final ChatLanguageModel model;

    private final ThreadPoolTaskExecutor taskExecutor;

    private final ReasonAgent reasonAgent;

    private final DeletionAgent deletionAgent;

    private final ObjectAgent objectAgent;

    private final ObjectEditionAgent objectEditionAgent;

    private final LinkAgent linkAgent;

    private final LinkEditionAgent linkEditionAgent;

    public RoutingAgent(ChatLanguageModel model, ReasonAgent reasonAgent, DeletionAgent deletionAgent,
                        ObjectAgent objectAgent, ObjectEditionAgent objectEditionAgent,
                        LinkAgent linkAgent, LinkEditionAgent linkEditionAgent,
                        @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.model = model;
        this.reasonAgent = reasonAgent;
        this.deletionAgent = deletionAgent;
        this.objectAgent = objectAgent;
        this.objectEditionAgent = objectEditionAgent;
        this.linkAgent = linkAgent;
        this.linkEditionAgent = linkEditionAgent;
        this.taskExecutor = taskExecutor;
    }

    public void compute(IInput input) {
        if (input instanceof AiRequestInput aiRequestInput) {
            List<ChatMessage> previousMessages = new ArrayList<>();
            List<ToolSpecification> specifications = new ArrayList<>();

            initializeSpecifications(List.of(this.objectAgent, this.deletionAgent, this.objectEditionAgent, this.linkAgent), aiRequestInput, List.of(), specifications);

            previousMessages.add(new SystemMessage("""
                    You are a routing agent for Diagram Generation.
                    From the user prompt, call the correct tools to create or edit a diagram.
                    You are encouraged to call multiple tools at the same time, since they are parallelized.
                    Start with the creation and deletion of objects. Once done, continue with the linking and edition of objects.
                    Do not hallucinate.
                    """));

            var concepts = this.reasonAgent.think(aiRequestInput.prompt());
            previousMessages.add(new UserMessage(concepts));

            previousMessages.add(new UserMessage("First, create objects accordingly."));
            ToolCallService.computeToolCalls(logger, this.model, previousMessages, List.of(), specifications, List.of(this.objectAgent, this.deletionAgent), this.taskExecutor);

            previousMessages.add(new UserMessage("Now, link objects together accordingly."));
            ToolCallService.computeToolCalls(logger, this.model, previousMessages, List.of(), specifications, List.of(this.linkAgent), this.taskExecutor);

            previousMessages.add(new UserMessage("If the previous tools (especially the linking tool) did not work, try something else. If it did work, do not call for tools."));
            ToolCallService.computeToolCalls(logger, this.model, previousMessages, List.of(), specifications, List.of(this.objectAgent, this.deletionAgent, this.linkAgent), this.taskExecutor);

            previousMessages.add(new UserMessage("Now, continue with the edition of objects and links accordingly."));
            ToolCallService.computeToolCalls(logger, this.model, previousMessages, List.of(), specifications, List.of(this.objectEditionAgent , this.linkEditionAgent), this.taskExecutor);
        }
    }
}

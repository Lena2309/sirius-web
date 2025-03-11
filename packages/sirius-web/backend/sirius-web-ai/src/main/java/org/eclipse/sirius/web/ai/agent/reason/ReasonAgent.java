package org.eclipse.sirius.web.ai.agent.reason;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.agent.Agent;
import org.eclipse.sirius.web.ai.tool.context.BuildContextTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ReasonAgent implements Agent {

    private final Logger logger = LoggerFactory.getLogger(ReasonAgent.class);

    private final ChatLanguageModel model;

    private final BuildContextTool buildContextTool;

    public ReasonAgent(BuildContextTool buildContextTool) {
        this.model = AiModelsConfiguration.buildLanguageModel(AiModelsConfiguration.ModelType.REASONING_MODEL);
        this.buildContextTool = Objects.requireNonNull(buildContextTool);
    }

    public void setInput(IInput input) {
        this.buildContextTool.setInput(input);
    }

    @Tool("List all the relevant and appropriate concepts that are necessary for the user's request in the context of the Diagram.")
    public String think(@P("The user's original prompt") String prompt) {
        var rateLimiter = AiModelsConfiguration.getRateLimiter(this.model);
        var context = this.buildContextTool.buildDomainContext();
        logger.info(context);

        var systemMessage = new SystemMessage("""
             You are a reasoning agent for diagram driven data Generation.
             Your purpose is to transform the user needs into a prompt that relies on the provided domain concepts.
             You are given a set of concepts, do not define or describe those concepts, but use them to build a representation that would satisfy the user's prompt.
             Your representation must be rich and complete. You have to be clear about what to create and what to link, as well as what special properties to set.
             Links and objects both have properties, specify what special properties to set when possible.
             You have to specify if the concept has to be created or if it should be modified/deleted from the existing diagram.
             Do not hallucinate. Here is the domain context, pay attention to the concepts that could share similar names but are still different:
             """ + context);

        var chatRequest = ChatRequest.builder()
                .messages(List.of(systemMessage, new UserMessage(prompt)))
                .parameters(ChatRequestParameters.builder()
                        .build())
                .build();

        logger.info("Rate limit is " + rateLimiter.getPermits());
        rateLimiter.acquire(logger);

        Instant responseStart = Instant.now();
        var rawResponse = this.model.chat(chatRequest);
        Instant responseFinish = Instant.now();

        long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
        logger.debug("Reason answered in {} ms", responseDuration);

        logger.info(rawResponse.toString());

        chatRequest.messages().add(rawResponse.aiMessage());

        return rawResponse.aiMessage().text();
    }
}

package org.eclipse.sirius.web.ai.agent.reason;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
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
        var context = this.buildContextTool.buildDomainContext();
        logger.info(context);

        List<ChatMessage> previousMessages = new ArrayList<>();
        previousMessages.add(new SystemMessage("""
             You are a reasoning agent for diagram driven data Generation.
             Your purpose is to transform the user needs into a prompt that relies on the provided domain concepts.
             You are given a set of concepts, do not define or describe those concepts, but use them to build a representation that would satisfy the user's prompt.
             Your representation must be rich and complete. You have to be clear about what to create and what to link, as well as what special properties to set.
             Do not hallucinate.
             """));
        previousMessages.add(new UserMessage("Here is the domain context:"+context));
        previousMessages.add(new UserMessage(prompt));

        ChatRequest request = new ChatRequest.Builder()
                .messages(previousMessages)
                .build();

        Instant responseStart = Instant.now();
        ChatResponse rawResponse = this.model.chat(request);
        Instant responseFinish = Instant.now();

        long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
        logger.warn("Reason answered in {} ms", responseDuration);


        logger.info(rawResponse.toString());

        previousMessages.add(rawResponse.aiMessage());

        return rawResponse.aiMessage().text();
    }
}

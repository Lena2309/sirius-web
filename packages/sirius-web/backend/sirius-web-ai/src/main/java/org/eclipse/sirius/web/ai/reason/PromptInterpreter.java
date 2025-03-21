package org.eclipse.sirius.web.ai.reason;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.reason.context.BuildContextTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

@Service
public class PromptInterpreter {

    private final Logger logger = LoggerFactory.getLogger(PromptInterpreter.class);

    private final ChatModel model;

    private final BuildContextTool buildContextTool;

    private final ResourceLoader resourceLoader;

    public PromptInterpreter(BuildContextTool buildContextTool, ResourceLoader resourceLoader) {
        this.model = AiModelsConfiguration.buildChatModel(AiModelsConfiguration.ModelType.REASON).get();
        this.buildContextTool = Objects.requireNonNull(buildContextTool);
        this.resourceLoader = Objects.requireNonNull(resourceLoader);
    }

    public void setInput(IInput input) {
        this.buildContextTool.setInput(input);
    }

    @Tool(description = "List all the relevant and appropriate concepts that are necessary for the user's request in the context of the Diagram.")
    public String think(@ToolParam(description = "The user's original prompt") String userPrompt) {
        var context = this.buildContextTool.buildDomainContext();

        var systemMessage = new SystemMessage("""
             You are a reasoning agent for data Generation.
             Your purpose is to transform the user needs into a prompt that relies on the provided domain concepts in the prompt.
             You must provide an answer, even if the domain is not suitable.
             Your representation must be rich and complete. You have to be clear about what to create and what to link, as well as what special properties to set.
             Links and objects both have properties, specify what special properties to set when possible.
             You have to specify if the concept has to be created or if it should be modified/deleted from the existing diagram.
             You have to be clear if a concept is the child of another.
             Do not hallucinate.
             """);

        var chatClient = ChatClient.builder(this.model)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();

        var prompt = new Prompt(systemMessage);

        loadFewShotExamples(prompt);

        prompt.getInstructions().add(new UserMessage("Considering the following domain, "+userPrompt+": \n"+context+"\n"+userPrompt));

//        Instant responseStart = Instant.now();

        var response = chatClient.prompt(prompt).call().content();

//        Instant responseFinish = Instant.now();

//        long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
//        logger.debug("Reason answered in {} ms", responseDuration);

        logger.info(response);
        return response;
    }

    private void loadFewShotExamples(Prompt prompt) {
        try {
            var prompts = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
                    .getResources("classpath*:prompts/**");
            var outputs = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
                    .getResources("classpath*:outputs/**");

            if (prompts.length != outputs.length) {
                throw new IOException("Different amount of prompts and answers for few-shot learning step.");
            }

            for (int i = 0; i < prompts.length; i++) {
                prompt.getInstructions().add(new UserMessage(prompts[i].getContentAsString(Charset.defaultCharset())));
                prompt.getInstructions().add(new AssistantMessage(outputs[i].getContentAsString(Charset.defaultCharset())));
            }

        } catch (Exception e) {
            logger.error("Error while loading few-shot learning: {}", e.getMessage());
        }
    }
}

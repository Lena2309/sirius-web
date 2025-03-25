package org.eclipse.sirius.web.ai.reason;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

        var messages = new ArrayList<Message>();
        messages.add(systemMessage);
        var prompt = new Prompt(messages);

        loadFewShotExamples(prompt);

        prompt.getInstructions().add(new UserMessage("Considering the following domain, "+userPrompt+": \n"+context+"\n"+userPrompt));

        var response = chatClient.prompt(prompt).call().content();

        logger.info(response);
        return response;
    }

    private void loadFewShotExamples(Prompt prompt) {
        try {
            var promptsFolder = new ClassPathResource("fewshot/prompts/");
            var outputsFolder = new ClassPathResource("fewshot/outputs/");

            var promptFiles = listResourceFiles(promptsFolder);
            var outputFiles = listResourceFiles(outputsFolder);

            if (promptFiles.size() != outputFiles.size()) {
                throw new IOException("Different amount of prompts and answers for few-shot learning step.");
            }

            for (int i = 0; i < promptFiles.size(); i++) {
                var promptText = readResourceAsString(promptsFolder.getPath() + promptFiles.get(i));
                var outputText = readResourceAsString(outputsFolder.getPath() + outputFiles.get(i));

                prompt.getInstructions().add(new UserMessage(promptText));
                prompt.getInstructions().add(new AssistantMessage(outputText));
            }

        } catch (Exception e) {
            logger.error("Error while loading few-shot learning: {}", e.getMessage());
        }
    }

    private List<String> listResourceFiles(ClassPathResource path) {
        var filenames = new ArrayList<String>();
        try {
            var stream = path.getInputStream();
            try {
                var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                String file;
                while ((file = reader.readLine()) != null) {
                    filenames.add(file);
                }
            } catch (Exception e) {
                logger.error("Error while reading file: {}", e.getMessage());
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return filenames;
    }

    private String readResourceAsString(String resourcePath) throws IOException {
        var inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Cannot read resource file: " + resourcePath);
        }
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

}
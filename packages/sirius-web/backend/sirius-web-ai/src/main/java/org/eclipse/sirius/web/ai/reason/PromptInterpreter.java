/***********************************************************************************************
 * Copyright (c) 2025 Obeo. All Rights Reserved.
 * This software and the attached documentation are the exclusive ownership
 * of its authors and was conceded to the profit of Obeo S.A.S.
 * This software and the attached documentation are protected under the rights
 * of intellectual ownership, including the section "Titre II  Droits des auteurs (Articles L121-1 L123-12)"
 * By installing this software, you acknowledge being aware of these rights and
 * accept them, and as a consequence you must:
 * - be in possession of a valid license of use conceded by Obeo only.
 * - agree that you have read, understood, and will comply with the license terms and conditions.
 * - agree not to do anything that could conflict with intellectual ownership owned by Obeo or its beneficiaries
 * or the authors of this software.
 *
 * Should you not agree with these terms, you must stop to use this software and give it back to its legitimate owner.
 ***********************************************************************************************/
package org.eclipse.sirius.web.ai.reason;

import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.reason.context.BuildContextTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.REASON;

@Service
public class PromptInterpreter {

    private final Logger logger = LoggerFactory.getLogger(PromptInterpreter.class);

    private final Optional<ChatModel> model;

    private final BuildContextTool buildContextTool;

    public PromptInterpreter(BuildContextTool buildContextTool, ResourceLoader resourceLoader) {
        this.model = AiModelsConfiguration.builder()
                .type(REASON)
                .build();
        this.buildContextTool = Objects.requireNonNull(buildContextTool);
    }

    public void setInput(IInput input) {
        this.buildContextTool.setInput(input);
    }

    public String think(String userPrompt) {
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

        assert this.model.isPresent();
        var chatClient = ChatClient.builder(this.model.get())
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();

        var messages = new ArrayList<Message>();

        messages.add(systemMessage);
        messages.addAll(loadFewShotExamples());
        messages.add(new UserMessage("Considering the following domain, "+userPrompt+": \n"+context+"\n"+userPrompt));

        var prompt = new Prompt(messages);
        var response = chatClient.prompt(prompt).call().content();

        logger.info(response);
        return response;
    }

    private ArrayList<Message> loadFewShotExamples() {
        var fewShotExamples = new ArrayList<Message>();
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

                fewShotExamples.add(new UserMessage(promptText));
                fewShotExamples.add(new AssistantMessage(outputText));
            }

        } catch (Exception e) {
            logger.error("Error while loading few-shot learning: {}", e.getMessage());
        }
        return fewShotExamples;
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
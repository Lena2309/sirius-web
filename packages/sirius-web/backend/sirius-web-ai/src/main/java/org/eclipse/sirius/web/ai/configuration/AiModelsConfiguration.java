package org.eclipse.sirius.web.ai.configuration;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;

public class AiModelsConfiguration {
    public static final ChatLanguageModel reasoningModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(GPT_4_O)
            .temperature(0.6)
            .build();

    public static final ChatLanguageModel orchestratorModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(GPT_4_O)
            .temperature(0.4)
            .build();

    public static final ChatLanguageModel diagramModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(GPT_4_O)
            .temperature(0.2)
            .build();

    public static final ChatLanguageModel editionModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(GPT_4_O)
            .temperature(0.5)
            .build();
}

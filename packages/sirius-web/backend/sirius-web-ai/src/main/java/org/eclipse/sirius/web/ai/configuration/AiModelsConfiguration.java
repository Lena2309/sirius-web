package org.eclipse.sirius.web.ai.configuration;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_LARGE_LATEST;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;

public class AiModelsConfiguration {
    public enum ModelType {
        ORCHESTRATION_MODEL,
        REASONING_MODEL,
        DIAGRAM_MODEL,
        EDITION_MODEL
    }

    public static ChatLanguageModel buildLanguageModel(ModelType type) {
        return switch (type) {
            case REASONING_MODEL -> MistralAiChatModel.builder()
                    .apiKey("eMEga7bjX71rW0jjwJd7wXmBGXH6advV")
                    .modelName(MISTRAL_LARGE_LATEST)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            case DIAGRAM_MODEL -> OpenAiChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(GPT_4_O)
                    .temperature(0.2)
                    .build();
            case ORCHESTRATION_MODEL -> OpenAiChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(GPT_4_O)
                    .temperature(0.4)
                    .build();
            case EDITION_MODEL -> OpenAiChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(GPT_4_O)
                    .temperature(0.5)
                    .build();
        };
    }
}

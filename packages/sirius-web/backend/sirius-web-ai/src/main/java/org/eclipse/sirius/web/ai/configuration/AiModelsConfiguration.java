package org.eclipse.sirius.web.ai.configuration;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;

import java.time.Duration;
import java.util.*;

import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_LARGE_LATEST;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;

public class AiModelsConfiguration {
    private static final Map<String, BlockingRateLimiter> rateLimiters = new HashMap<>();

    public enum ModelType {
        ORCHESTRATION_MODEL,
        REASONING_MODEL,
        DIAGRAM_MODEL,
        EDITION_MODEL
    }

    public static ChatLanguageModel buildLanguageModel(ModelType type) {
        return switch (type) {
            case REASONING_MODEL -> MistralAiChatModel.builder()
                    .apiKey(System.getenv("MISTRAL_API_KEY"))
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

    // TODO: add specificities based on model type
    public static BlockingRateLimiter getRateLimiter(ChatLanguageModel model) {
        BlockingRateLimiter rateLimiter;
        if (model instanceof MistralAiChatModel) {
            if (!rateLimiters.containsKey("mistral")) {
                rateLimiters.put("mistral", new BlockingRateLimiter(5, Duration.ofSeconds(2)));
            }
            rateLimiter = rateLimiters.get("mistral");
        } else {
            if (!rateLimiters.containsKey("openai")) {
                rateLimiters.put("openai", new BlockingRateLimiter(500, Duration.ofMinutes(1)));
            }
            rateLimiter = rateLimiters.get("openai");
        }
        return rateLimiter;
    }

    public static void executionDone() {
        for (var rateLimiter : rateLimiters.values()) {
            rateLimiter.done();
        }
    }
}

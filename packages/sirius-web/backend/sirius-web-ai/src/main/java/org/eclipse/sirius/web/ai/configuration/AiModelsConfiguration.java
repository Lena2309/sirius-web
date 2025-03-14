package org.eclipse.sirius.web.ai.configuration;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_HAIKU_20240307;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_LARGE_LATEST;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;

public class AiModelsConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(AiModelsConfiguration.class);

    private static final Map<String, BlockingRateLimiter> rateLimiters = new HashMap<>();

    public enum ModelType {
        ORCHESTRATION,
        REASON,
        DIAGRAM,
        EDITION
    }

    private static String getDefaultModel(String agent) {
        return switch (agent) {
            case "mistral-ai" -> String.valueOf(MISTRAL_LARGE_LATEST);
            case "open-ai" -> String.valueOf(GPT_4_O);
            case "anthropic" -> String.valueOf(CLAUDE_3_HAIKU_20240307);
            default -> "";
        };
    }

    private static double getDefaultTemperature(ModelType type) {
        return switch (type) {
            case REASON -> 0.5;
            case DIAGRAM -> 0.3;
            case ORCHESTRATION, EDITION -> 0.4;
        };
    }

    private static String getModelName(String propertyModelName, String defaultModelName) {
        if (propertyModelName == null) {
            return defaultModelName;
        }
        return propertyModelName;
    }

    public static Optional<ChatLanguageModel> buildChatModel(ModelType type) {
        String agentProperty;
        String modelNameProperty;
        if (type == ModelType.REASON) {
            agentProperty = System.getProperty("reason-agent");
            modelNameProperty = System.getProperty("reason-agent-model-name");
        } else {
            agentProperty = System.getProperty("tooling-agent");
            modelNameProperty = System.getProperty("tooling-agent-model-name");
        }

        return Optional.ofNullable(agentProperty).map(it -> createChatModel(it, modelNameProperty, getDefaultTemperature(type)));
    }

    private static ChatLanguageModel createChatModel(String agent, String modelNameProp, double temperature) {
        var modelName = getModelName(modelNameProp, getDefaultModel(agent));

        return switch (agent) {
            case "mistral-ai" -> MistralAiChatModel.builder()
                    .apiKey(System.getProperty("mistral-ai-api-key"))
                    .modelName(modelName)
                    .temperature(temperature)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            case "open-ai" -> OpenAiChatModel.builder()
                    .apiKey(System.getProperty("open-ai-api-key"))
                    .modelName(modelName)
                    .temperature(temperature)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            case "anthropic" -> AnthropicChatModel.builder()
                    .apiKey(System.getProperty("anthropic-api-key"))
                    .modelName(modelName)
                    .temperature(temperature)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            default -> {
                logger.warn("Unsupported or undefined agent: {}", agent);
                yield null;
            }
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

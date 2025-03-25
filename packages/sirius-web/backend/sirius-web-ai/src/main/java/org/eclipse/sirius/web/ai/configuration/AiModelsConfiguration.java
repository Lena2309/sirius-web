package org.eclipse.sirius.web.ai.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;

import java.util.Optional;

public class AiModelsConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(AiModelsConfiguration.class);

    public enum ModelType {
        ORCHESTRATION,
        REASON,
        DIAGRAM,
        EDITION
    }

    private static String getDefaultModel(String agent) {
        return switch (agent) {
            case "mistral-ai" -> "mistral-large-latest";
            case "open-ai" -> "gpt-4o";
            case "anthropic" -> "claude-3-haiku-20240307";
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

    public static Optional<ChatModel> buildChatModel(ModelType type) {
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

    private static ChatModel createChatModel(String agent, String modelNameProp, double temperature) {
        var modelName = getModelName(modelNameProp, getDefaultModel(agent));

        return switch (agent) {
            case "mistral-ai" -> MistralAiChatModel.builder()
                    .mistralAiApi(new MistralAiApi(System.getProperty("mistral-ai-api-key")))
                    .retryTemplate(RetryTemplate.defaultInstance())
                    .defaultOptions(MistralAiChatOptions.builder()
                            .model(modelName)
                            .temperature(temperature)
                            .build())
                    .build();
            case "open-ai" -> OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder().apiKey(System.getProperty("open-ai-api-key")).build())
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(modelName)
                            .temperature(temperature)
                            .build())
                    .build();

            case "anthropic" -> AnthropicChatModel.builder()
                    .anthropicApi(new AnthropicApi(System.getProperty("open-ai-api-key")))
                    .defaultOptions(AnthropicChatOptions.builder()
                            .model(modelName)
                            .temperature(temperature)
                            .build())
                    .build();

            default -> {
                logger.warn("Unsupported or undefined model for agent: {}", agent);
                yield null;
            }
        };
    }
}
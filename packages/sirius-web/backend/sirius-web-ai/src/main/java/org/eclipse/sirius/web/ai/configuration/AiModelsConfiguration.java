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
import org.springframework.retry.policy.AlwaysRetryPolicy;
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

    private AiModelsConfiguration() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ModelType type;

        public Builder type(ModelType type) {
            this.type = type;
            return this;
        }

        public Optional<ChatModel> build() {
            if (type == null) {
                logger.warn("Model type is not defined");
                return Optional.empty();
            }

            String agentProperty;
            String modelNameProperty;
            if (type == ModelType.REASON) {
                agentProperty = System.getProperty("reason-agent");
                modelNameProperty = System.getProperty("reason-agent-model-name");
            } else {
                agentProperty = System.getProperty("tooling-agent");
                modelNameProperty = System.getProperty("tooling-agent-model-name");
            }

            return Optional.ofNullable(agentProperty).map(agent -> createChatModel(agent, modelNameProperty, getDefaultTemperature(type)));
        }

        private double getDefaultTemperature(ModelType type) {
            return switch (type) {
                case REASON, EDITION -> 0.5;
                case DIAGRAM, ORCHESTRATION -> 0.4;
            };
        }

        private String getDefaultModel(String agent) {
            return switch (agent) {
                case "mistral-ai" -> "mistral-large-latest";
                case "open-ai" -> "gpt-4o";
                case "anthropic" -> "claude-3-haiku-20240307";
                default -> "";
            };
        }

        private ChatModel createChatModel(String agent, String modelNameProp, double temperature) {
            var modelName = modelNameProp != null ? modelNameProp : getDefaultModel(agent);
            var retryPolicy = RetryTemplate.defaultInstance();
            retryPolicy.setRetryPolicy(new AlwaysRetryPolicy());

            return switch (agent) {
                case "mistral-ai" -> MistralAiChatModel.builder()
                        .mistralAiApi(new MistralAiApi(System.getProperty("mistral-ai-api-key")))
                        .retryTemplate(retryPolicy)
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
}
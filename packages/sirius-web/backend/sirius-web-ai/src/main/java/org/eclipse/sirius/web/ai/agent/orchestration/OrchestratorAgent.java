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
package org.eclipse.sirius.web.ai.agent.orchestration;

import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.agent.Agent;
import org.eclipse.sirius.web.ai.agent.diagram.*;
import org.eclipse.sirius.web.ai.reason.PromptInterpreter;
import org.eclipse.sirius.web.ai.dto.AiRequestInput;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.web.ai.tool.service.ToolCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.ORCHESTRATION;

@Service
public class OrchestratorAgent implements Agent {
    private final Logger logger = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final ThreadPoolTaskExecutor executor;

    private final Optional<ChatModel> model;

    private final PromptInterpreter promptInterpreter;

    private final DeletionAgent deletionAgent;

    private final ObjectCreationAgent objectCreationAgent;

    private final LinkCreationAgent linkCreationAgent;

    public OrchestratorAgent(PromptInterpreter promptInterpreter, DeletionAgent deletionAgent,
                             ObjectCreationAgent objectCreationAgent, LinkCreationAgent linkCreationAgent,
                             ThreadPoolTaskExecutor executor) {
        this.executor = executor;
        this.model = AiModelsConfiguration.builder()
                .type(ORCHESTRATION)
                .build();
        this.promptInterpreter = Objects.requireNonNull(promptInterpreter);
        this.deletionAgent = Objects.requireNonNull(deletionAgent);
        this.objectCreationAgent = Objects.requireNonNull(objectCreationAgent);
        this.linkCreationAgent = Objects.requireNonNull(linkCreationAgent);
    }

    public void compute(IInput input) {
        if (input instanceof AiRequestInput aiRequestInput) {
            initializeSpecifications(List.of(this.deletionAgent, this.objectCreationAgent, this.linkCreationAgent), aiRequestInput);
            this.promptInterpreter.setInput(aiRequestInput);

            var systemMessage = new SystemMessage("""
                    You are an orchestrating agent for Diagram Generation.
                    From the user prompt and the list of concepts computed from it, call the correct tools to create or edit a diagram.
                    You must call tools.
                    Start with the creation and deletion of objects. Once done, continue with the linking and edition of objects.
                    Do not hallucinate.
                    
                    Call the tools in batches:
                        1. Object Creations and Deletions
                        2. Link Creations and Deletions
                    The batches must be separate, but you can make multiple tool calls at the same time per batches.
                    """);

            var concepts = this.promptInterpreter.think(aiRequestInput.prompt());

            var prompt = new Prompt(systemMessage, new UserMessage(concepts));

            assert this.model.isPresent();
            new ToolCallService().computeToolCalls(this.logger, this.model.get(), prompt, this.executor, this.objectCreationAgent, this.linkCreationAgent, this.deletionAgent);

            logger.info("Orchestrator done !");
        }
    }
}
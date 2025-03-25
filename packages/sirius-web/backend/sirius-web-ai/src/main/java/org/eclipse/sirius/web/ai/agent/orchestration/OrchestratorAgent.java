package org.eclipse.sirius.web.ai.agent.orchestration;

import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.agent.Agent;
import org.eclipse.sirius.web.ai.agent.diagram.*;
import org.eclipse.sirius.web.ai.reason.PromptInterpreter;
import org.eclipse.sirius.web.ai.dto.AiRequestInput;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration.ModelType.ORCHESTRATION;

@Service
public class OrchestratorAgent implements Agent {
    private final Logger logger = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final ChatModel model;

    private final PromptInterpreter promptInterpreter;

    private final DeletionAgent deletionAgent;

    private final ObjectCreationAgent objectCreationAgent;

    private final LinkCreationAgent linkCreationAgent;

    private final ThreadPoolTaskExecutor taskExecutor;

    public OrchestratorAgent(PromptInterpreter promptInterpreter, DeletionAgent deletionAgent,
                             ObjectCreationAgent objectCreationAgent, LinkCreationAgent linkCreationAgent,
                             @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.model = AiModelsConfiguration.buildChatModel(ORCHESTRATION).get();
        this.promptInterpreter = Objects.requireNonNull(promptInterpreter);
        this.deletionAgent = Objects.requireNonNull(deletionAgent);
        this.objectCreationAgent = Objects.requireNonNull(objectCreationAgent);
        this.linkCreationAgent = Objects.requireNonNull(linkCreationAgent);
        this.taskExecutor = Objects.requireNonNull(taskExecutor);
    }

    public void compute(IInput input) {
        if (input instanceof AiRequestInput aiRequestInput) {
            initializeSpecifications(List.of(this.deletionAgent, this.objectCreationAgent, this.linkCreationAgent), aiRequestInput);
            this.promptInterpreter.setInput(aiRequestInput);

            var systemMessage = new SystemMessage("""
                    You are an orchestrating agent for Diagram Generation.
                    From the user prompt and the list of concepts computed from it, call the correct tools to create or edit a diagram.
                    You are encouraged to call multiple tools at the same time, since they are parallelized.
                    Start with the creation and deletion of objects. Once done, continue with the linking and edition of objects.
                    Do not hallucinate.
                    
                    Call the tools in batches:
                        1. Object Creations and Deletions
                        2. Link Creations and Deletions
                    The batches must be separate, but you can make multiple tool calls at the time per batches.
                    """);

            var concepts = this.promptInterpreter.think(aiRequestInput.prompt());

            var prompt = new Prompt(systemMessage, new UserMessage(concepts));

//        Instant responseStart = Instant.now();

            var response = ChatClient.builder(this.model)
                    .build()
                    .prompt(prompt)
                    .advisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                    .tools(this.deletionAgent, this.objectCreationAgent, this.linkCreationAgent)
                    .call()
                    .content();

            logger.info("Prompt response: {}", response);
        }
    }
}
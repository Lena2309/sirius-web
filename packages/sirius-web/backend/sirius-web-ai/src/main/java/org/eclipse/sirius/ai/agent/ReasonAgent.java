package org.eclipse.sirius.ai.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.tool.getter.ObjectGetterTools;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReasonAgent {

    private final Logger logger = LoggerFactory.getLogger(ReasonAgent.class);

    private final ChatLanguageModel model;

    private final CreationAgent creationAgent;

    private final ObjectGetterTools objectGetter;

    public ReasonAgent(ChatLanguageModel model, CreationAgent creationAgent, ObjectGetterTools objectGetter) {
        this.model = model;
        this.creationAgent = creationAgent;
        this.objectGetter = objectGetter;
    }

    public void compute(IInput input) {
        if (input instanceof AiRequestInput aiRequestInput) {
            List<ChatMessage> previousMessages = new ArrayList<>();

            previousMessages.add(new SystemMessage("""
                    You are an agent for Diagram Generation.
                    From the user's prompt, list all the concepts/objects that are relevant and appropriate.
                    Do not forget the links between the objects.
                    Your output should be optimized for a LLM because your answer will serve to call tools in an ulterior process.
                    Also mention important additional information that the other LLM could need/use.
                    """));

            previousMessages.add(new UserMessage(aiRequestInput.prompt()));

            ChatRequest request = new ChatRequest.Builder()
                    .messages(previousMessages)
                    .toolSpecifications()
                    .build();

            ChatResponse rawResponse = this.model.chat(request);
            logger.info(rawResponse.toString());

            previousMessages.add(rawResponse.aiMessage());

            this.creationAgent.setInput(aiRequestInput);
            this.creationAgent.generate(rawResponse.aiMessage().text());
        }
    }
}

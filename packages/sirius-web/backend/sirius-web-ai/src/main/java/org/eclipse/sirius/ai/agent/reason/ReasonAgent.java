package org.eclipse.sirius.ai.agent.reason;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.eclipse.sirius.ai.agent.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReasonAgent implements Agent {

    private final Logger logger = LoggerFactory.getLogger(ReasonAgent.class);

    private final ChatLanguageModel model;

    public ReasonAgent(ChatLanguageModel model) {
        this.model = model;
    }

    @Tool("List all the relevant and appropriate concepts that are necessary for the user's request in the context of the Diagram.")
    public String think(@P("The user's original prompt") String prompt) {
        List<ChatMessage> previousMessages = new ArrayList<>();

        previousMessages.add(new SystemMessage("""
             You are an agent for Diagram Generation.
             From the user's prompt, list all the concepts/objects that are relevant and appropriate.
             Do not forget links between the objects. Do not forget to set special values for properties, if necessary.
             Mention if the concept must be created, deleted or edited. Unless knowing they already exist, create the objects and links.
             Your output should be concise and without ambiguity, it will be used by another LLM in an ulterior process.
             Here is an example:
             - Create Composite Processor (Composite Processor 1)
                - Create child Processor (Processor 1)
                    - Edit Object Property: Status: inactive
             - Create Data Source (Data Source 1)
                - Link To Processor 1
             """));

        previousMessages.add(new UserMessage(prompt));

        ChatRequest request = new ChatRequest.Builder()
                .messages(previousMessages)
                .toolSpecifications()
                .build();

        ChatResponse rawResponse = this.model.chat(request);
        logger.info(rawResponse.toString());

        previousMessages.add(rawResponse.aiMessage());

        return rawResponse.aiMessage().text();
    }
}

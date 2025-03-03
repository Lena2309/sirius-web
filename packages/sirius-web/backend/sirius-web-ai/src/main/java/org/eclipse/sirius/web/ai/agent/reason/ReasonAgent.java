package org.eclipse.sirius.web.ai.agent.reason;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.agent.Agent;
import org.eclipse.sirius.web.ai.tool.context.BuildContextTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReasonAgent implements Agent {

    private final Logger logger = LoggerFactory.getLogger(ReasonAgent.class);

    private final ChatLanguageModel model;

    private final BuildContextTool buildContextTool;

    public ReasonAgent(BuildContextTool buildContextTool) {
        this.model = AiModelsConfiguration.buildLanguageModel(AiModelsConfiguration.ModelType.REASONING_MODEL);
        this.buildContextTool = Objects.requireNonNull(buildContextTool);
    }

    public void setInput(IInput input) {
        this.buildContextTool.setInput(input);
    }

    @Tool("List all the relevant and appropriate concepts that are necessary for the user's request in the context of the Diagram.")
    public String think(@P("The user's original prompt") String prompt) {
        var context = this.buildContextTool.buildDomainContext();
        logger.info(context);

        List<ChatMessage> previousMessages = new ArrayList<>();
/*
        previousMessages.add(new SystemMessage("""
             You are an agent for Diagram Generation.
             From the user's prompt and the domain context, list all the concepts/objects that are relevant and appropriate.
             Do not forget links between the objects. Do not forget to set special values for properties, if necessary.
             Mention if the concept must be created, deleted or edited. Unless knowing they already exist, create the objects and links.
             Your output should be concise and without ambiguity, it will be used by another LLM in an ulterior process.
             Here is an example:
             - Create Composite Processor (Composite Processor 1)
                - Create child Processor (Processor 1)
                    - Edit Object Property: Status: inactive
             - Create Data Source (Data Source 1)
                - Link To Processor 1

             Example:
                Liens disponibles:
                    - Brique to Brique
                    - Toiture to Mur
                    - Fenetre to Mur
                    - Fenetre to Toiture
                    - Porte to Mur

                Objects disponibles
                    - Mur
                        - Brique
                    - Toiture
                    - Porte
                    - Fenetre
                    - Pelouse
                    - Piscine

                Prompt: "Fabrique une maison"
                Réponse: "
                Une maison a 4 murs, chacun contient 10 briques liées entre elles. Une maison a une toiture liées aux murs.
                Une maison a 1 porte liée à l'un des murs, elle devient une porte d'entrée.
                Elle a 2 fenêtres par mur et une fenêtre sur la toiture.
               "
             """));
*/
        previousMessages.add(new SystemMessage("""
             You are a reasoning agent for diagram driven data Generation.
             Your purpose is to transform the user needs into a prompt that relies on the provided domain concepts.
             You are given a set of concepts, do not define or describe those concepts, but use them to build a representation that would satisfy the user's prompt.
             Your representation must be rich and complete. You have to be clear about what to create and what to link, as well as what special properties to set.
             Do not hallucinate.
             """));
        previousMessages.add(new UserMessage("Here is the domain context:"+context));
        previousMessages.add(new UserMessage(prompt));

        ChatRequest request = new ChatRequest.Builder()
                .messages(previousMessages)
                .toolSpecifications()
                .build();

        this.model.generate(previousMessages);
        ChatResponse rawResponse = this.model.chat(request);
        logger.info(rawResponse.toString());

        previousMessages.add(rawResponse.aiMessage());

        return rawResponse.aiMessage().text();
    }
}

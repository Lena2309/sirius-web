package org.eclipse.sirius.ai.agent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.eclipse.sirius.ai.tool.AiTools;
import org.eclipse.sirius.ai.tool.edition.LinkEditionTools;
import org.eclipse.sirius.ai.tool.edition.ObjectEditionTools;
import org.eclipse.sirius.ai.tool.getter.LinkGetterTools;
import org.eclipse.sirius.ai.tool.getter.ObjectGetterTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EditionAgent extends ToolAgent {

    public EditionAgent(ChatLanguageModel model, ObjectGetterTools objectGetterTools, ObjectEditionTools objectEditionTools,
                     LinkGetterTools linkGetterTools, LinkEditionTools linkEditionTools) {
        super(model, objectGetterTools, linkGetterTools);
        this.toolClasses.add(objectEditionTools);
        this.toolClasses.add(linkEditionTools);
        setLogger(this);
    }

    @Tool("Edit an object's properties.")
    public void editObject(@P("Explaining what properties to modify if possible, with their ideal values.") String prompt, @P("The object id to edit.") String objectId) {
        List<ChatMessage> previousMessages = new ArrayList<>();
        List<ToolSpecification> specifications = new ArrayList<>();

        for (AiTools toolClass : toolClasses) {
            specifications.addAll(ToolSpecifications.toolSpecificationsFrom(toolClass));
        }

        previousMessages.add(new SystemMessage("""
            You are an assistant for Diagram Object Edition.
            Do not write any text, just call the correct tools to edit the correct diagram element given in the user's request .
            Do not hallucinate.
            """
        ));

        previousMessages.add(new UserMessage("Here is the diagram element id: " + objectId + ". " + prompt));

        computeToolCalls(previousMessages, specifications);
    }
}

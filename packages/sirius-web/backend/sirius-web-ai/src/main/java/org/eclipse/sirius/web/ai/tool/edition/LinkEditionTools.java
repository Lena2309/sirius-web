package org.eclipse.sirius.web.ai.tool.edition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.web.ai.dto.AgentResult;
import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class LinkEditionTools implements AiTool {
    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final AiToolService aiToolService;

    private final EditionToolService editionToolService;

    public LinkEditionTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                              AiToolService aiToolService, EditionToolService editionToolService) {
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
        this.aiToolService = Objects.requireNonNull(aiToolService);
        this.editionToolService = Objects.requireNonNull(editionToolService);
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
        this.editionToolService.setAiToolService(this.aiToolService);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  GET LINK PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a Map of an existing link properties structured as {property label, [property value options]} OR {property label, property current value}.")
    public Map<String, Map<String, Object>> getLinkProperties(@P("The link's Id to edit.") String linkId) {
        var form = this.editionToolService.getFormForObject(linkId, false);

        return this.editionToolService.getProperties(form);
    }

    @Tool("Call this tool when editing an object's property is absolutely impossible in any way, shape or form. If there is a property that could be similar try editing it and do not call this tool.")
    public AgentResult unableToEditProperty(@P("The object's Id to edit.") String objectId, String propertyLabel) {
        return new AgentResult("unableToEditProperty", "The property "+propertyLabel+" of "+objectId+" either does not exist or is not modifiable. Try something else.");
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT LINK PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Edit the value of an existing link property.")
    public AgentResult editLinkSingleValueProperty(@P("The link's Id to edit.") String linkId, @P("The (existing) property to edit.") String propertyLabel, @P("The new value.") String newPropertyValue) {
        var linkEdge = this.aiToolService.findEdge(UUIDConverter.decompress(linkId).toString());
        Objects.requireNonNull(linkEdge);
        var representationId = new StringBuilder("details://?objectIds=[").append(linkEdge.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(linkId, propertyLabel, false);

        if (widget.isEmpty()) {
            return new AgentResult("editObjectSingleValueProperty", "Property "+propertyLabel+" of "+linkId+" does not exist.");
        }

        return new AgentResult("editLinkSingleValueProperty", this.editionToolService.changePropertySingleValue(newPropertyValue, widget.get(), representationId, this.editingContextEventProcessorRegistry));
    }

    @Tool("Edit the values of an existing link property that can contain multiple ones at once.")
    public AgentResult editLinkMultipleValueProperty(@P("The link's Id to edit.") String linkId, @P("The (existing) property to edit.") String propertyLabel, @P("The new values.") List<String> newPropertyValues) {
        var linkEdge = this.aiToolService.findEdge(UUIDConverter.decompress(linkId).toString());
        Objects.requireNonNull(linkEdge);
        var representationId = new StringBuilder("details://?objectIds=[").append(linkEdge.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(linkId, propertyLabel, false);

        if (widget.isEmpty()) {
            return new AgentResult("editObjectSingleValueProperty", "Property "+propertyLabel+" of "+linkId+" does not exist.");
        }

        return new AgentResult("editLinkMultipleValueProperty", this.editionToolService.changePropertyMultipleValue(newPropertyValues, widget.get(), representationId, this.editingContextEventProcessorRegistry));
    }
}

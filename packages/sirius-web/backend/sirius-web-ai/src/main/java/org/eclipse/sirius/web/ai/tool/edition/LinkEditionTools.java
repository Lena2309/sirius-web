package org.eclipse.sirius.web.ai.tool.edition;

import org.eclipse.sirius.web.ai.tool.service.AiDiagramService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.codec.UUIDCodec;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class LinkEditionTools implements AiTool {
    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final AiDiagramService aiDiagramService;

    private final EditionToolService editionToolService;

    public LinkEditionTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                            AiDiagramService aiDiagramService, EditionToolService editionToolService) {
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
        this.aiDiagramService = Objects.requireNonNull(aiDiagramService);
        this.editionToolService = Objects.requireNonNull(editionToolService);
    }

    @Override
    public void setInput(IInput input) {
        this.aiDiagramService.setInput(input);
        this.editionToolService.setDiagramService(this.aiDiagramService);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  GET LINK PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool(description = "Retrieve a Map of an existing link properties structured as {property label, [property value options]} OR {property label, property current value}.")
    public Map<String, Map<String, Object>> getLinkProperties(@ToolParam(description = "The link's Id to edit.") String linkId) {
        var form = this.editionToolService.getFormForObject(linkId, false);

        return this.editionToolService.getProperties(form);
    }

    @Tool(description = "Call this tool when editing an object's property is absolutely impossible in any way, shape or form. If there is a property that could be similar try editing it and do not call this tool.")
    public String unableToEditProperty(@ToolParam(description = "The object's Id to edit.") String objectId, String propertyLabel) {
        return "The property "+propertyLabel+" of "+objectId+" either does not exist or is not modifiable. Try something else.";
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT LINK PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool(description = "Edit the value of an existing link property.")
    public String editLinkSingleValueProperty(@ToolParam(description = "The link's Id to edit.") String linkId, @ToolParam(description = "The (existing) property to edit.") String propertyLabel, @ToolParam(description = "The new value.") String newPropertyValue) {
        var linkEdge = this.aiDiagramService.findEdge(new UUIDCodec().decompress(linkId).toString());
        Objects.requireNonNull(linkEdge);
        var representationId = new StringBuilder("details://?objectIds=[").append(linkEdge.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(linkId, propertyLabel, false);

        if (widget.isEmpty()) {
            return "Property "+propertyLabel+" of "+linkId+" does not exist.";
        }

        return this.editionToolService.changePropertySingleValue(newPropertyValue, widget.get(), representationId, this.editingContextEventProcessorRegistry);
    }

    @Tool(description = "Edit the values of an existing link property that can contain multiple ones at once.")
    public String editLinkMultipleValueProperty(@ToolParam(description = "The link's Id to edit.") String linkId, @ToolParam(description = "The (existing) property to edit.") String propertyLabel, @ToolParam(description = "The new values.") List<String> newPropertyValues) {
        var linkEdge = this.aiDiagramService.findEdge(new UUIDCodec().decompress(linkId).toString());
        Objects.requireNonNull(linkEdge);
        var representationId = new StringBuilder("details://?objectIds=[").append(linkEdge.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(linkId, propertyLabel, false);

        if (widget.isEmpty()) {
            return "Property "+propertyLabel+" of "+linkId+" does not exist.";
        }

        return this.editionToolService.changePropertyMultipleValue(newPropertyValues, widget.get(), representationId, this.editingContextEventProcessorRegistry);
    }
}

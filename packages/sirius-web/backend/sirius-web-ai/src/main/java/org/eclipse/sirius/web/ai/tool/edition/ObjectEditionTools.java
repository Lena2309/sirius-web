package org.eclipse.sirius.web.ai.tool.edition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.web.ai.dto.AgentResult;
import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.diagrams.dto.EditLabelInput;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.diagrams.OutsideLabel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ObjectEditionTools implements AiTool {
    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final AiToolService aiToolService;

    private final EditionToolService editionToolService;

    public ObjectEditionTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
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
    //                                                  GET OBJECT PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a Map of an existing object properties structured as {property label, [property value options]} OR {property label, property current value}")
    public Map<String, Map<String, Object>> getAvailableObjectProperties(@P("The object's Id to edit.") String objectId) {
        var form = this.editionToolService.getFormForObject(objectId, true);

        return this.editionToolService.getProperties(form);
    }

    @Tool("Call this tool when editing an object's property is absolutely impossible in any way, shape or form. If there is a property that could be similar try editing it and do not call this tool.")
    public AgentResult unableToEditProperty(@P("The object's Id to edit.") String objectId, String propertyLabel) {
        return new AgentResult("unableToEditProperty", "The property "+propertyLabel+" of "+objectId+" either does not exist or is not modifiable. Try something else.");
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT OBJECT PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Edit the value of an existing object's single valued property.")
    public AgentResult editObjectSingleValueProperty(@P("The object's Id to edit.") String objectId, @P("The (existing) property to edit.") String propertyLabel, @P("The new value.") String newPropertyValue) {
        UUID decompressedObjectId;

        try {
            decompressedObjectId = UUIDConverter.decompress(objectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Object id is not in the correct format.");
        }

        var objectNode = this.aiToolService.findNode(decompressedObjectId.toString());
        Objects.requireNonNull(objectNode);
        var representationId = new StringBuilder("details://?objectIds=[").append(objectNode.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(objectId, propertyLabel, true);

        if (widget.isEmpty()) {
            return new AgentResult("editObjectSingleValueProperty", "Property "+propertyLabel+" of "+objectId+" does not exist.");
        }

        return new AgentResult("editObjectSingleValueProperty", this.editionToolService.changePropertySingleValue(newPropertyValue, widget.get(), representationId, this.editingContextEventProcessorRegistry));
    }

    @Tool("Edit the values of an existing object's multiple valued property.")
    public AgentResult editObjectMultipleValueProperty(@P("The object's Id to edit.") String objectId, @P("The (existing) property to edit.") String propertyLabel, @P("The new values.") List<String> newPropertyValues) {
        UUID decompressedObjectId;

        try {
            decompressedObjectId = UUIDConverter.decompress(objectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Object id is not in the correct format.");
        }

        var objectNode = this.aiToolService.findNode(decompressedObjectId.toString());
        Objects.requireNonNull(objectNode);
        var representationId = new StringBuilder("details://?objectIds=[").append(objectNode.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(objectId, propertyLabel, true);

        if (widget.isEmpty()) {
            return new AgentResult("editObjectSingleValueProperty", "Property "+propertyLabel+" of "+objectId+" does not exist.");
        }

        return new AgentResult("editObjectMultipleValueProperty", this.editionToolService.changePropertyMultipleValue(newPropertyValues, widget.get(), representationId, this.editingContextEventProcessorRegistry));
    }
}

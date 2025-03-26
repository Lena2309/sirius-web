package org.eclipse.sirius.web.ai.tool.edition;

import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ObjectEditionTools implements AiTool {
    private final static Logger logger = LoggerFactory.getLogger(ObjectEditionTools.class);
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

    @Tool(description = "Retrieve a Map of an existing object properties structured as {property label, [property value options]} OR {property label, property current value}")
    public Map<String, Map<String, Object>> getAvailableObjectProperties(@ToolParam(description = "The object's Id to edit.") String objectId) {
        var form = this.editionToolService.getFormForObject(objectId, true);

        var prop = this.editionToolService.getProperties(form);
        logger.info("Retrieved available properties for {}: {}", objectId, prop);
        return prop;
    }

    @Tool(description = "Call this tool when editing an object's property is absolutely impossible in any way, shape or form. If there is a property that could be similar try editing it and do not call this tool.")
    public String unableToEditProperty(@ToolParam(description = "The object's Id to edit.") String objectId, String propertyLabel) {
        return "The property "+propertyLabel+" of "+objectId+" either does not exist or is not modifiable. Try something else.";
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT OBJECT PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool(description = "Edit the value of an existing object's single valued property.")
    public String editObjectSingleValueProperty(@ToolParam(description = "The object's Id to edit.") String objectId, @ToolParam(description = "The (existing) property to edit.") String propertyLabel, @ToolParam(description = "The new value.") String newPropertyValue) {
        logger.info("Edit the property {} of {} to {}", propertyLabel, objectId, newPropertyValue);
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
            return "Property "+propertyLabel+" of "+objectId+" does not exist.";
        }

        return this.editionToolService.changePropertySingleValue(newPropertyValue, widget.get(), representationId, this.editingContextEventProcessorRegistry);
    }

    @Tool(description = "Edit the values of an existing object's multiple valued property.")
    public String editObjectMultipleValueProperty(@ToolParam(description = "The object's Id to edit.") String objectId, @ToolParam(description = "The (existing) property to edit.") String propertyLabel, @ToolParam(description = "The new values.") List<String> newPropertyValues) {
        logger.info("Edit the property {} of {} to {}", propertyLabel, objectId, newPropertyValues);
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
            return "Property "+propertyLabel+" of "+objectId+" does not exist.";
        }

        return this.editionToolService.changePropertyMultipleValue(newPropertyValues, widget.get(), representationId, this.editingContextEventProcessorRegistry);
    }
}

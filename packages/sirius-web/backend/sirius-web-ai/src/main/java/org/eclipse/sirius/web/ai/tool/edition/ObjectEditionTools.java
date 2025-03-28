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
package org.eclipse.sirius.web.ai.tool.edition;

import org.eclipse.sirius.web.ai.tool.service.AiDiagramService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.codec.UUIDCodec;
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

    private final AiDiagramService aiDiagramService;

    private final EditionToolService editionToolService;

    public ObjectEditionTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
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
    //                                                  GET OBJECT PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool(description = "Retrieve a Map of an existing object properties structured as {property label, [property value options]} OR {property label, property current value}")
    public Map<String, Map<String, Object>> getAvailableObjectProperties(@ToolParam(description = "The object's Id to edit.") String objectId) {
        var form = this.editionToolService.getFormForObject(objectId, true);

        var prop = this.editionToolService.getProperties(form);
        logger.info("Retrieving available properties for {}: {}", objectId, prop);
        return prop;
    }

    @Tool(description = "Call this tool when editing an object's property is absolutely impossible in any way, shape or form. If there is a property that could be similar try editing it and do not call this tool.")
    public String unableToEditProperty(@ToolParam(description = "The object's Id to edit.") String objectId, String propertyLabel) {
        return "The property "+propertyLabel+" of "+objectId+" either does not exist or is not modifiable.";
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT OBJECT PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool(description = "")
    public String editObjectCheckboxProperty(@ToolParam(description = "The object's Id to edit.") String objectId, @ToolParam(description = "The (existing) property to edit.") String propertyLabel, boolean checked) {
        UUID decompressedObjectId;

        try {
            decompressedObjectId = new UUIDCodec().decompress(objectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Object id is not in the correct format.");
        }

        var objectNode = this.aiDiagramService.findNode(decompressedObjectId.toString());
        Objects.requireNonNull(objectNode);
        var representationId = new StringBuilder("details://?objectIds=[").append(objectNode.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(objectId, propertyLabel, true);

        if (widget.isEmpty()) {
            return "Property "+propertyLabel+" of "+objectId+" does not exist.";
        }

        return this.editionToolService.changeCheckboxProperty(checked, widget.get(), representationId, this.editingContextEventProcessorRegistry);
    }

    @Tool(description = "Edit the value of an existing object's single valued property.")
    public String editObjectSingleValueProperty(@ToolParam(description = "The object's Id to edit.") String objectId, @ToolParam(description = "The (existing) property to edit.") String propertyLabel, @ToolParam(description = "The new value.") String newPropertyValue) {
        UUID decompressedObjectId;

        try {
            decompressedObjectId = new UUIDCodec().decompress(objectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Object id is not in the correct format.");
        }

        var objectNode = this.aiDiagramService.findNode(decompressedObjectId.toString());
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
        UUID decompressedObjectId;

        try {
            decompressedObjectId = new UUIDCodec().decompress(objectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Object id is not in the correct format.");
        }

        var objectNode = this.aiDiagramService.findNode(decompressedObjectId.toString());
        Objects.requireNonNull(objectNode);
        var representationId = new StringBuilder("details://?objectIds=[").append(objectNode.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(objectId, propertyLabel, true);

        if (widget.isEmpty()) {
            return "Property "+propertyLabel+" of "+objectId+" does not exist.";
        }

        return this.editionToolService.changePropertyMultipleValue(newPropertyValues, widget.get(), representationId, this.editingContextEventProcessorRegistry);
    }
}

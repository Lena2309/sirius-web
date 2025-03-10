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
    public Map<String, Map<String, Object>> getAvailableObjectProperties(String objectId) {
        var form = this.editionToolService.getFormForObject(objectId, true);

        return this.editionToolService.getProperties(form);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT OBJECT PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Edit the value of an existing object's single valued property.")
    public AgentResult editObjectSingleValueProperty(String objectId, String propertyLabel, String newPropertyValue) {
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

        return new AgentResult("editObjectSingleValueProperty", this.editionToolService.changePropertySingleValue(newPropertyValue, widget, representationId, this.editingContextEventProcessorRegistry));
    }

    @Tool("Edit the values of an existing object's multiple valued property.")
    public AgentResult editObjectMultipleValueProperty(String objectId, String propertyLabel, List<String> newPropertyValues) {
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

        return new AgentResult("editObjectMultipleValueProperty", this.editionToolService.changePropertyMultipleValue(newPropertyValues, widget, representationId, this.editingContextEventProcessorRegistry));
    }
}

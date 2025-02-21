package org.eclipse.sirius.web.ai.tool.edition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
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
        this.editingContextEventProcessorRegistry = editingContextEventProcessorRegistry;
        this.aiToolService = aiToolService;
        this.editionToolService = editionToolService;
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
    public Map<String, Map<String, Object>> getObjectProperties(String objectId) {
        var form = this.editionToolService.getFormForObject(objectId, true);

        return this.editionToolService.getProperties(form);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT OBJECT LABEL
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Edit the label of an existing object.")
    public String editObjectLabel(@P("The object's label Id to edit.") String objectId, String newLabel) {
        this.aiToolService.refreshDiagram();

            var labelId = this.aiToolService.getDiagram().getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), UUIDConverter.decompress(objectId).toString()))
                    .map(node -> {
                        List<OutsideLabel> outsideLabels = node.getOutsideLabels();
                        if(!outsideLabels.isEmpty()) {
                            return outsideLabels.get(0).id();
                        }
                        return node.getInsideLabel().getId();
                    })
                    .findFirst()
                    .orElse(null);

            assert labelId != null;
            var diagramInput = new EditLabelInput(
                    UUID.randomUUID(),
                    this.aiToolService.getEditingContextId(),
                    this.aiToolService.getRepresentationId(),
                    labelId,
                    newLabel
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            return "Success";
    }

    @Tool("Edit the label of an existing object's child.")
    public String editChildLabel(String parentId, String childId, String newLabel) {
        this.aiToolService.refreshDiagram();

            var labelId = this.aiToolService.getDiagram().getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), UUIDConverter.decompress(parentId).toString()))
                    .map(node -> {
                        String childLabelId = node.getChildNodes().stream()
                                .filter(childNode -> Objects.equals(childNode.getId(), UUIDConverter.decompress(childId).toString()))
                                .map(childNode -> childNode.getOutsideLabels().get(0).id())
                                .findFirst()
                                .orElse(null);

                        assert childLabelId != null;
                        return childLabelId;
                    }).findFirst()
                    .orElse(null);

            var diagramInput = new EditLabelInput(
                    UUID.randomUUID(),
                    this.aiToolService.getEditingContextId(),
                    this.aiToolService.getRepresentationId(),
                    labelId,
                    newLabel
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            return "Success";
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT OBJECT PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Edit the value of an existing object property.")
    public String editObjectSingleValueProperty(String objectId, String propertyLabel, String newPropertyValue) {
        this.aiToolService.refreshDiagram();

        var objectNode = this.aiToolService.findNode(UUIDConverter.decompress(objectId).toString());
        assert objectNode != null;
        var representationId = new StringBuilder("details://?objectIds=[").append(objectNode.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(objectId, propertyLabel, true);

        return this.editionToolService.changePropertySingleValue(newPropertyValue, widget, representationId, this.editingContextEventProcessorRegistry);
    }

    @Tool("Edit the values of an existing object property that can contain multiple ones at once.")
    public String editObjectMultipleValueProperty(String objectId, String propertyLabel, List<String> newPropertyValues) {
        var objectNode = this.aiToolService.findNode(UUIDConverter.decompress(objectId).toString());
        assert objectNode != null;
        var representationId = new StringBuilder("details://?objectIds=[").append(objectNode.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(objectId, propertyLabel, true);

        return this.editionToolService.changePropertyMultipleValue(newPropertyValues, widget, representationId, this.editingContextEventProcessorRegistry);
    }
}

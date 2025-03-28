package org.eclipse.sirius.web.ai.tool.creation;

import org.eclipse.sirius.components.collaborative.diagrams.dto.*;
import org.eclipse.sirius.web.ai.tool.service.AiDiagramService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.dto.PairDiagramElement;
import org.eclipse.sirius.web.ai.codec.UUIDCodec;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;


@Service
public class ObjectCreationTools implements AiTool {
    private static Logger logger = LoggerFactory.getLogger(ObjectCreationTools.class);

    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final AiDiagramService aiDiagramService;

    private final List<String> objectIds = new ArrayList<>();

    public ObjectCreationTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                               AiDiagramService aiDiagramService) {
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
        this.aiDiagramService = Objects.requireNonNull(aiDiagramService);
    }

    @Override
    public void setInput(IInput input) {
        this.aiDiagramService.setInput(input);
    }

    public List<String> getObjectIds() {
        return this.objectIds;
    }

    public void clearObjectIds() {
        this.objectIds.clear();
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                          OBJECT CREATION OPERATION GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool(description = "Retrieve the list of available creation operations at root structured as {type of the object to create, operation id}")
    public List<PairDiagramElement> getAvailableRootObjectCreationOperations() {
        var paletteInput = new GetPaletteInput(
                UUID.randomUUID(),
                this.aiDiagramService.getEditingContextId(),
                this.aiDiagramService.getDiagramId(),
                this.aiDiagramService.getDiagramId()
        );

        return getCreationTools(paletteInput);
    }

    @Tool(description = "Retrieve the list of available child creation operations structured as {type of the child to create, operation id}")
    public List<PairDiagramElement> getAvailableChildCreationOperations(@ToolParam(description = "The parent id.") String parentId) {
        UUID parentIdConverted;
        try {
            parentIdConverted = new UUIDCodec().decompress(parentId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Parent id is not in the correct format.");
        }

        var paletteInput = new GetPaletteInput(
                UUID.randomUUID(),
                this.aiDiagramService.getEditingContextId(),
                this.aiDiagramService.getDiagramId(),
                parentIdConverted.toString()
        );

        return getCreationTools(paletteInput);
    }

    private List<PairDiagramElement> getCreationTools(IInput input) {
        if (input instanceof GetPaletteInput paletteInput) {
            var creationTools = new ArrayList<PairDiagramElement>();
            var payload = new AtomicReference<Mono<IPayload>>();

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(paletteInput.editingContextId())
                    .ifPresent(processor -> payload.set(processor.handle(paletteInput)));

            Objects.requireNonNull(payload.get());
            payload.get().subscribe(invokePayload -> {
                if (invokePayload instanceof GetPaletteSuccessPayload successPayload) {
                    successPayload.palette().paletteEntries().stream()
                            .filter(ToolSection.class::isInstance)
                            .filter(toolSection -> !((ToolSection) toolSection).label().equals("Show/Hide"))
                            .forEach(toolSection -> {
                                for (var tool : ((ToolSection) toolSection).tools()) {
                                    try {
                                        creationTools.add(new PairDiagramElement(tool.label(), new UUIDCodec().compress(tool.id())));
                                    } catch (Exception ignored) {}
                                }
                            });
                }
            });

            return creationTools;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  OPERATION EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool(description = "Perform the creation operation at root. Returns the new object's id. The id should not be modified.")
    public String createObjectAtRoot(@ToolParam(description = "The id of the operation to execute.") String operationId, @ToolParam(description = "The type of the object to create.") String objectType) {
        UUID decompressedOperationId;

        try {
            decompressedOperationId = new UUIDCodec().decompress(operationId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Operation id is not in the correct format.");
        }

        var diagramInput = new InvokeSingleClickOnDiagramElementToolInput(
                UUID.randomUUID(),
                this.aiDiagramService.getEditingContextId(),
                this.aiDiagramService.getDiagramId(),
                this.aiDiagramService.getDiagramId(),
                decompressedOperationId.toString(),
                0.0,
                0.0,
                List.of()
        );

        var newObjectId = this.aiDiagramService.createNewNode(this.editingContextEventProcessorRegistry, diagramInput, diagramInput.editingContextId());

        if (newObjectId == null) {
            return "Failed to create new Object.";
        }

        var result = objectType + " created at root with id : " + new UUIDCodec().compress(newObjectId);
        this.objectIds.add(result);

        return result;
    }

    @Tool(description = "Perform the creation operation. Returns the new child's id. The id should not be modified.")
    public String createChild(@ToolParam(description = "The parent's id.") String parentId, @ToolParam(description = "The type of the child.") String childType, @ToolParam(description = "The id of the operation to perform.") String operationId) {
        UUID decompressedOperationId;
        UUID decompressedParentId;

        try {
            decompressedOperationId = new UUIDCodec().decompress(operationId);
            decompressedParentId = new UUIDCodec().decompress(parentId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Parent or Child id is not in the correct format.");
        }

        var diagramInput = new InvokeSingleClickOnDiagramElementToolInput(
                UUID.randomUUID(),
                this.aiDiagramService.getEditingContextId(),
                this.aiDiagramService.getDiagramId(),
                decompressedParentId.toString(),
                decompressedOperationId.toString(),
                0.0,
                0.0,
                List.of()
        );

        var newObjectId = this.aiDiagramService.createNewChild(this.editingContextEventProcessorRegistry, diagramInput, diagramInput.editingContextId(), parentId);

        if (newObjectId == null) {
            return "Failed to create new Child.";
        }

        var result = childType + ", child of " + parentId + ", created with id : " + new UUIDCodec().compress(newObjectId);
        this.objectIds.add(result);

        return result;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  RENAME OBJECT
    // ---------------------------------------------------------------------------------------------------------------

    @Tool(description = "rename an existing object.")
    private String renameObject(@ToolParam(description = "The object's Id to rename.") String objectId, String newName) {
        UUID decompressedObjectId;

        try {
            decompressedObjectId = new UUIDCodec().decompress(objectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Object id is not in the correct format.");
        }

        var node = this.aiDiagramService.findNode(decompressedObjectId.toString());

        if (node == null) {
            return "Object was not found";
        }

        String labelId;

        var outsideLabels = node.getOutsideLabels();
        if(!outsideLabels.isEmpty()) {
            labelId = outsideLabels.get(0).id();
        } else {
            labelId = node.getInsideLabel().getId();
        }

        Objects.requireNonNull(labelId);
        var diagramInput = new EditLabelInput(
                UUID.randomUUID(),
                this.aiDiagramService.getEditingContextId(),
                this.aiDiagramService.getDiagramId(),
                labelId,
                newName
        );

        var payload = new AtomicReference<Mono<IPayload>>();
        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                .ifPresent(processor -> payload.set(processor.handle(diagramInput)));

        var failedMessages = "Failed to rename " + objectId + " to " + newName + ". Maybe the correct way is to rename the object through its properties, by editing general properties.";
        var result = new AtomicReference<>(failedMessages);

        payload.get().subscribe(invokePayload -> {
            if (invokePayload instanceof EditLabelSuccessPayload) {
                result.set("Success");
            }
        });

        node = this.aiDiagramService.findNode(decompressedObjectId.toString());

        if (!Objects.equals(node.getTargetObjectLabel(), newName)) {
            return failedMessages;
        }

        return result.get();
    }
}
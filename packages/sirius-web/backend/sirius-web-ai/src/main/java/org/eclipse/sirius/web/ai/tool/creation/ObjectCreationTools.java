package org.eclipse.sirius.web.ai.tool.creation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.components.collaborative.diagrams.dto.*;
import org.eclipse.sirius.web.ai.dto.AgentResult;
import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.util.PairDiagramElement;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
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
    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final AiToolService aiToolService;

    public ObjectCreationTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                               AiToolService aiToolService) {
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
        this.aiToolService = Objects.requireNonNull(aiToolService);
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                          OBJECT CREATION OPERATION GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve the list of available creation operations at root structured as {type of the object to create, operation id}")
    public List<PairDiagramElement> getAvailableRootObjectCreationOperations() {
        var paletteInput = new GetPaletteInput(
                UUID.randomUUID(),
                this.aiToolService.getEditingContextId(),
                this.aiToolService.getRepresentationId(),
                this.aiToolService.getRepresentationId()
        );

        return getCreationTools(paletteInput);
    }

    @Tool("Retrieve the list of available child creation operations structured as {type of the child to create, operation id}")
    public List<PairDiagramElement> getAvailableChildCreationOperations(@P("The parent id.") String parentId) {
        UUID parentIdConverted;
        try {
            parentIdConverted = UUIDConverter.decompress(parentId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Parent id is not in the correct format.");
        }

        var paletteInput = new GetPaletteInput(
                UUID.randomUUID(),
                this.aiToolService.getEditingContextId(),
                this.aiToolService.getRepresentationId(),
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
                                        creationTools.add(new PairDiagramElement(tool.label(), UUIDConverter.compress(tool.id())));
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

    @Tool("Perform the creation operation at root. Returns the new object's id. The id should not be modified.")
    public AgentResult createObjectAtRoot(@P("The id of the operation to execute.") String operationId, @P("The type of the object to create.") String objectType) {
        UUID decompressedOperationId;

        try {
            decompressedOperationId = UUIDConverter.decompress(operationId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Operation id is not in the correct format.");
        }

        var diagramInput = new InvokeSingleClickOnDiagramElementToolInput(
                UUID.randomUUID(),
                this.aiToolService.getEditingContextId(),
                this.aiToolService.getRepresentationId(),
                this.aiToolService.getRepresentationId(),
                decompressedOperationId.toString(),
                0.0,
                0.0,
                List.of()
        );

        var newObjectId = this.aiToolService.createNewNode(this.editingContextEventProcessorRegistry, diagramInput, diagramInput.editingContextId(), null);

        if (newObjectId == null) {
            return new AgentResult("createObjectAtRoot", "Failed to create new Object.");
        }

        return new AgentResult("createObjectAtRoot", objectType + " created at root with id : " + UUIDConverter.compress(newObjectId));
    }

    @Tool("Perform the creation operation. Returns the new child's id. The id should not be modified.")
    public AgentResult createChild(@P("The parent's id.") String parentId, @P("The type of the child.") String childType, @P("The id of the operation to perform.") String operationId) {
        UUID decompressedOperationId;
        UUID decompressedParentId;

        try {
            decompressedOperationId = UUIDConverter.decompress(operationId);
            decompressedParentId = UUIDConverter.decompress(parentId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Parent or Child id is not in the correct format.");
        }

        var diagramInput = new InvokeSingleClickOnDiagramElementToolInput(
                UUID.randomUUID(),
                this.aiToolService.getEditingContextId(),
                this.aiToolService.getRepresentationId(),
                decompressedParentId.toString(),
                decompressedOperationId.toString(),
                0.0,
                0.0,
                List.of()
        );

        var newObjectId = this.aiToolService.createNewNode(this.editingContextEventProcessorRegistry, diagramInput, diagramInput.editingContextId(), parentId);

        if (newObjectId == null) {
            return new AgentResult("createChild", "Failed to create new Child.");
        }

        return new AgentResult("createChild", childType + ", child of " + parentId + ", created with id : " + UUIDConverter.compress(newObjectId));
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  RENAME OBJECT
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("rename an existing object.")
    public String renameObject(@P("The object's Id to rename.") String objectId, String newName) {
        UUID decompressedObjectId;

        try {
            decompressedObjectId = UUIDConverter.decompress(objectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Object id is not in the correct format.");
        }

        var node = this.aiToolService.findNode(decompressedObjectId.toString());

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
                this.aiToolService.getEditingContextId(),
                this.aiToolService.getRepresentationId(),
                labelId,
                newName
        );

        var payload = new AtomicReference<Mono<IPayload>>();
        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                .ifPresent(processor -> payload.set(processor.handle(diagramInput)));

        var result = new AtomicReference<>("Failed to rename " + objectId + " to " + newName + ".");
        payload.get().subscribe(invokePayload -> {
            if (invokePayload instanceof EditLabelSuccessPayload) {
                result.set("Success");
            }
        });

        return result.get();
    }
}
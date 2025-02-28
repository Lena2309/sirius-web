package org.eclipse.sirius.web.ai.tool.creation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.components.collaborative.diagrams.dto.*;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
                            .filter(toolSection -> ((ToolSection) toolSection).label().contains("Creation"))
                            .forEach(toolSection -> {
                                for (var tool : ((ToolSection) toolSection).tools()) {
                                    creationTools.add(new PairDiagramElement(tool.label(), UUIDConverter.compress(tool.id())));
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
    public String createObjectAtRoot(@P("The id of the operation to execute.") String operationId) {
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

        this.aiToolService.refreshDiagram();
        var alreadyExistingObjects = new ArrayList<>();

        for (var node : this.aiToolService.getDiagram().getNodes()) {
            alreadyExistingObjects.add(node.getId());
        }

        String newObjectId = null;

        var payload = new AtomicReference<Mono<IPayload>>();
        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                .ifPresent(processor -> payload.set(processor.handle(diagramInput)));

        var objectCreated = new AtomicBoolean(false);
        payload.get().subscribe(invokePayload -> {
            if (invokePayload instanceof InvokeSingleClickOnDiagramElementToolSuccessPayload) {
                objectCreated.set(true);
            }
        });

        if (!objectCreated.get()) {
            return "Failed to create object.";
        }

        this.aiToolService.refreshDiagram();

        for (var node : this.aiToolService.getDiagram().getNodes()) {
            if (!alreadyExistingObjects.contains(node.getId())) {
                newObjectId = node.getId();
            }
        }

        return UUIDConverter.compress(newObjectId);
    }

    @Tool("Perform the creation operation. Returns the new child's id. The id should not be modified.")
    public String createChild(@P("The parent's id.") String parentId, @P("The type of the child.") String childType, @P("The id of the operation to perform.") String operationId) {
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

        var parentNode = this.aiToolService.findNode(UUIDConverter.decompress(parentId).toString());
        var alreadyExistingChildren = parentNode.getChildNodes();

        String newChildId = null;

        var payload = new AtomicReference<Mono<IPayload>>();
        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                .ifPresent(processor -> payload.set(processor.handle(diagramInput)));

        var objectCreated = new AtomicBoolean(false);
        payload.get().subscribe(invokePayload -> {
            if (invokePayload instanceof InvokeSingleClickOnDiagramElementToolSuccessPayload) {
                objectCreated.set(true);
            }
        });

        if (!objectCreated.get()) {
            return "Failed to create child.";
        }

        this.aiToolService.refreshDiagram();

        parentNode = this.aiToolService.findNode(UUIDConverter.decompress(parentId).toString());

        var newChildren = parentNode.getChildNodes().stream()
                .filter(child -> !alreadyExistingChildren.contains(child)).toList();

        for (var child : newChildren) {
            if (child.getTargetObjectKind().contains(childType.replace(" ", ""))) {
                newChildId = child.getId();
                break;
            }
        }

        return UUIDConverter.compress(newChildId);
    }
}
package org.eclipse.sirius.ai.tool.creation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.tool.AiTools;
import org.eclipse.sirius.ai.util.PairDiagramElement;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetPaletteInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetPaletteSuccessPayload;
import org.eclipse.sirius.components.collaborative.diagrams.dto.InvokeSingleClickOnDiagramElementToolInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.ToolSection;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetPaletteEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;

import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.diagrams.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ObjectCreationTools extends AiTools {
    private final Logger logger = LoggerFactory.getLogger(ObjectCreationTools.class);

    public ObjectCreationTools(IRepresentationSearchService representationSearchService,
                               IEditingContextSearchService editingContextSearchService,
                               @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                               GetPaletteEventHandler paletteEventHandler) {
        super(representationSearchService, editingContextSearchService, editingContextEventProcessorRegistry, paletteEventHandler);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                          OBJECT CREATION OPERATION GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve the list of available creation operations at root structured as {type of the object to create, operation id}")
    public List<PairDiagramElement> getAvailableRootObjectCreationOperations() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            this.refreshDiagram();

            var paletteInput = new GetPaletteInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    aiRequestInput.representationId()
            );

            return getCreationTools(paletteInput);
        }
        return null;
    }

    @Tool("Retrieve the list of available child creation operations structured as {type of the child to create, operation id}")
    public List<PairDiagramElement> getAvailableChildCreationOperations(@P("The parent id.") String parentId) {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            this.refreshDiagram();

            var paletteInput = new GetPaletteInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    UUIDConverter.decompress(parentId).toString()
            );

            return getCreationTools(paletteInput);
        }
        return null;
    }

    private List<PairDiagramElement> getCreationTools(IInput input) {
        if (input instanceof GetPaletteInput paletteInput) {
            var creationTools = new ArrayList<PairDiagramElement>();
            var payload = new AtomicReference<Mono<IPayload>>();

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(paletteInput.editingContextId())
                    .ifPresent(processor -> payload.set(processor.handle(paletteInput)));

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
        if (this.input instanceof AiRequestInput aiRequestInput) {
            var diagramInput = new InvokeSingleClickOnDiagramElementToolInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    aiRequestInput.representationId(),
                    UUIDConverter.decompress(operationId).toString(),
                    0.0,
                    0.0,
                    List.of()
            );

            this.refreshDiagram();
            var alreadyExistingObjects = new ArrayList<>();

            for (Node node : this.diagram.getNodes()) {
                alreadyExistingObjects.add(node.getId());
            }

            var newObjectId = "Failed to create object.";

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            this.refreshDiagram();

            for (Node node : this.diagram.getNodes()) {
                if (!alreadyExistingObjects.contains(node.getId())) {
                    newObjectId = node.getId();
                }
            }

            logger.info("Existing nodes : {}", alreadyExistingObjects);
            logger.info("Created new node with id {}", newObjectId);

            return UUIDConverter.compress(newObjectId);
        }
        return "The input is not of type AiRequestInput.";
    }

    @Tool("Perform the creation operation. Returns the new child's id. The id should not be modified.")
    public String createChild(@P("The id of the operation to perform.") String operationId, @P("The parent's id .") String parentId) {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            var diagramInput = new InvokeSingleClickOnDiagramElementToolInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    UUIDConverter.decompress(parentId).toString(),
                    UUIDConverter.decompress(operationId).toString(),
                    0.0,
                    0.0,
                    List.of()
            );

            this.refreshDiagram();

            var parentNode = this.diagram.getNodes().stream()
                    .filter(node -> Objects.equals(UUIDConverter.decompress(parentId).toString(), node.getId()))
                    .findFirst();
            assert parentNode.isPresent();
            var alreadyExistingChildren = new ArrayList<>(parentNode.get().getChildNodes());

            var newChildId = "Failed to create object.";

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            this.refreshDiagram();

            logger.info("Existing children : {}", alreadyExistingChildren);

            parentNode = this.diagram.getNodes().stream()
                    .filter(node -> Objects.equals(UUIDConverter.decompress(parentId).toString(), node.getId()))
                    .findFirst();

            assert parentNode.isPresent();
            var newChild = parentNode.get().getChildNodes().stream()
                    .filter(child -> !alreadyExistingChildren.contains(child))
                    .findFirst();

            assert newChild.isPresent();
            newChildId = newChild.get().getId();

            logger.info("Created new child with id {} from parent {}", newChildId, UUIDConverter.decompress(parentId));

            return UUIDConverter.compress(newChildId);
        }
        return "The input is not of type AiRequestInput.";
    }
}

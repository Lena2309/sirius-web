package org.eclipse.sirius.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.util.PairDiagramElement;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.collaborative.diagrams.dto.*;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetPaletteEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;

import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.OutsideLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiObjectTools extends AiTools {
    private static final Logger log = LoggerFactory.getLogger(AiObjectTools.class);

    public AiObjectTools(IRepresentationSearchService representationSearchService,
                         IEditingContextSearchService editingContextSearchService,
                         @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                         GetPaletteEventHandler paletteEventHandler) {
        super(representationSearchService, editingContextSearchService, editingContextEventProcessorRegistry, paletteEventHandler);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                               DIAGRAM ELEMENTS GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a List of existing root object IDs structured as: {object type, object id}")
    public List<PairDiagramElement> getExistingObjectsIds() throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            var availableObjects = new ArrayList<PairDiagramElement>();

            this.diagram = this.getDiagram(aiRequestInput);

            for (var node : this.diagram.getNodes()) {
                availableObjects.add(new PairDiagramElement(node.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=",""), UUIDConverter.compress(node.getId())));
            }

            return availableObjects;
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    @Tool("Retrieve the list of children elements. Useless for freshly created objects, or when creating objects at root.")
    public List<PairDiagramElement> getChildrenIds(@P("The parent object. Not the diagram root.") String parentObjectId) throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            var availableChildNodes = new ArrayList<PairDiagramElement>();

            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            var diagramElement = this.diagram.getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), UUIDConverter.decompress(parentObjectId).toString()))
                    .findFirst()
                    .orElse(null);

            assert diagramElement != null;
            for (var node : diagramElement.getChildNodes()) {
                availableChildNodes.add(new PairDiagramElement(node.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=",""), UUIDConverter.compress(node.getId())));
            }

            return availableChildNodes;
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve the list of available creation operations at root structured as {type of the object to create, operation id}")
    public List<PairDiagramElement> getAvailableRootObjectCreationOperations() throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            var paletteInput = new GetPaletteInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    aiRequestInput.representationId()
            );

            return getCreationTools(paletteInput);
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    @Tool("Retrieve the list of available child creation operations structured as {type of the child to create, operation id}")
    public List<PairDiagramElement> getAvailableChildCreationOperations(@P("The parent id.") String parentId) throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            var paletteInput = new GetPaletteInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    UUIDConverter.decompress(parentId).toString()
            );

            return getCreationTools(paletteInput);
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    private List<PairDiagramElement> getCreationTools(IInput input) throws Exception {
        if (input instanceof GetPaletteInput paletteInput) {
            var creationTools = new ArrayList<PairDiagramElement>();

            Sinks.One<IPayload> payloadSink = Sinks.one();

            this.diagramEventHandler.handle(payloadSink, Sinks.many().unicast().onBackpressureBuffer(), this.getEditingContext(this.input), new DiagramContext(diagram), paletteInput);

            var payloadMono = payloadSink.asMono();

            payloadMono.subscribe(payload -> {
                if (payload instanceof GetPaletteSuccessPayload getPaletteSuccessPayload) {
                    getPaletteSuccessPayload.palette().paletteEntries().stream()
                            .filter(ToolSection.class::isInstance)
                            .filter(toolSection -> Objects.equals(((ToolSection) toolSection).label(), "Creation Tools"))
                            .forEach(toolSection -> {
                                for (var tool : ((ToolSection) toolSection).tools()) {
                                    creationTools.add(new PairDiagramElement(tool.label(), UUIDConverter.compress(tool.id())));
                                }
                            });
                }
            }, throwable -> System.err.println("Failed to retrieve payload: " + throwable.getMessage()));

            return creationTools;
        }
        throw new Exception("Wrong type of input.");
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Perform the creation operation at root. Returns the new object's id.")
    public String createObjectAtRoot(@P("The id of the operation to execute.") String operationId) throws Exception {
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

            var newObjectId = createObject(diagramInput);

            this.diagram = this.getDiagram(aiRequestInput);
            return newObjectId;
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    @Tool("Perform the creation operation. Returns the new child's id.")
    public String createChild(@P("The id of the operation to perform.") String operationId, @P("The parent's id .") String parentId) throws Exception {
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

            var newChildId = createObject(diagramInput);

            this.diagram = this.getDiagram(aiRequestInput);
            return newChildId;
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    private String createObject(IInput input) {
        var newObjectId = new AtomicReference<>("Failed to create object.");

        if (input instanceof InvokeSingleClickOnDiagramElementToolInput diagramInput) {
            var payload = new AtomicReference<Mono<IPayload>>();

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> payload.set(processor.handle(diagramInput)));

            payload.get().subscribe(invokePayload -> {
                if (invokePayload instanceof InvokeSingleClickOnDiagramElementToolSuccessPayload successPayload) {
                    newObjectId.set(successPayload.newSelection().getEntries().get(0).getId());
                }
            });
        }

        return UUIDConverter.compress(newObjectId.get());
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT TOOLS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Edit the label of a diagram's object.")
    public String editObjectLabel(@P("The object's label Id to edit.") String objectId, String newLabel) throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            var labelId = diagram.getNodes().stream()
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
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    labelId,
                    newLabel
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            return "Success";
        }
        return "Failure";
    }

    @Tool("Edit the label of an object's child.")
    public String editChildLabel(String parentId, String childId, String newLabel) throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            var labelId = diagram.getNodes().stream()
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
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    labelId,
                    newLabel
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            return "Success";
        }
        return "Failure";
    }
}

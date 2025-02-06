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

    @Tool("Retrieve the diagram's root id.")
    public String getDiagramRootId() throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            return UUIDConverter.compress(aiRequestInput.representationId());
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    @Tool("Retrieve a List of existing Diagram Object IDs structured as: {object type, object id}")
    public List<PairDiagramElement> getExistingDiagramObjectsIds() throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            List<PairDiagramElement> availableObjects = new ArrayList<>();

            this.diagram = this.getDiagram(aiRequestInput);

            for (Node node : this.diagram.getNodes()) {
                availableObjects.add(new PairDiagramElement(node.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=",""), UUIDConverter.compress(node.getId())));
            }

            return availableObjects;
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    @Tool("Retrieve the list of children elements. Useless for freshly created objects, or when creating objects at root.")
    public List<PairDiagramElement> getChildrenIds(@P("The parent object. Not the diagram root.") String parentObjectId) throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            List<PairDiagramElement> availableChildNodes = new ArrayList<>();

            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            Node diagramElement = this.diagram.getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), UUIDConverter.decompress(parentObjectId).toString()))
                    .findFirst()
                    .orElse(null);

            assert diagramElement != null;
            for (Node node : diagramElement.getChildNodes()) {
                availableChildNodes.add(new PairDiagramElement(node.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=",""), UUIDConverter.compress(node.getId())));
            }

            return availableChildNodes;
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL GETTERS
    // ---------------------------------------------------------------------------------------------------------------

  //  @SystemMessage("You must use another tool to retrieve existing diagram element ids.")
    @Tool("Retrieve the list of available creation operations structured as {name of the object to create, operation id}")
    public List<PairDiagramElement> getAvailableObjectCreationTools(@P("The diagram object id. Use another tool to retrieve the existing ones.") String diagramObjectId) throws Exception {
        List<PairDiagramElement> creationTools = new ArrayList<>();

        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            GetPaletteInput paletteInput = new GetPaletteInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    UUIDConverter.decompress(diagramObjectId).toString()
            );

            Sinks.One<IPayload> payloadSink = Sinks.one();

            this.diagramEventHandler.handle(payloadSink, Sinks.many().unicast().onBackpressureBuffer(), this.getEditingContext(aiRequestInput), new DiagramContext(diagram), paletteInput);

            Mono<IPayload> payloadMono = payloadSink.asMono();

            payloadMono.subscribe(payload -> {
                if (payload instanceof GetPaletteSuccessPayload getPaletteSuccessPayload) {
                    getPaletteSuccessPayload.palette().paletteEntries().stream()
                            .filter(ToolSection.class::isInstance)
                            .filter(toolSection -> Objects.equals(((ToolSection) toolSection).label(), "Creation Tools"))
                            .forEach(toolSection -> {
                                for (ITool tool : ((ToolSection) toolSection).tools()) {
                                    creationTools.add(new PairDiagramElement(tool.label(), UUIDConverter.compress(tool.id())));
                                }
                            });
                }
            }, throwable -> System.err.println("Failed to retrieve payload: " + throwable.getMessage()));
        }

        return creationTools;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

   // @SystemMessage("You must use another tool to choose the correct diagram tool.")
    @Tool("Perform the creation operation. Returns the new object's id.")
    public String executeObjectTool(@P("The id of the operation to execute.") String diagramToolId, @P("The id of the context where the tool will be executed in.") String diagramObjectId) throws Exception {
        AtomicReference<String> newObjectId = new AtomicReference<>("Failed to create object.");

        if (this.input instanceof AiRequestInput aiRequestInput) {
            InvokeSingleClickOnDiagramElementToolInput diagramInput = new InvokeSingleClickOnDiagramElementToolInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    UUIDConverter.decompress(diagramObjectId).toString(),
                    UUIDConverter.decompress(diagramToolId).toString(),
                    0.0,
                    0.0,
                    List.of()
            );

            AtomicReference<Mono<IPayload>> payload = new AtomicReference<>();

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
    public String editObjectLabel(@P("The object's label Id to edit.") String diagramObjectId, String newLabel) throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            String labelId = diagram.getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), UUIDConverter.decompress(diagramObjectId).toString()))
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
            EditLabelInput diagramInput = new EditLabelInput(
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
    public String editChildLabel(String parentDiagramElementId, String childDiagramElementId, String newLabel) throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            String labelId = diagram.getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), UUIDConverter.decompress(parentDiagramElementId).toString()))
                    .map(node -> {
                        String childLabelId = node.getChildNodes().stream()
                                .filter(childNode -> Objects.equals(childNode.getId(), UUIDConverter.decompress(childDiagramElementId).toString()))
                                .map(childNode -> childNode.getOutsideLabels().get(0).id())
                                .findFirst()
                                .orElse(null);

                        assert childLabelId != null;
                        return childLabelId;
                    }).findFirst()
                    .orElse(null);

            EditLabelInput diagramInput = new EditLabelInput(
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

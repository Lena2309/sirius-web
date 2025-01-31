package org.eclipse.sirius.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.SystemMessage;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.collaborative.diagrams.dto.*;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetConnectorToolsEventHandler;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetPaletteEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.components.diagrams.OutsideLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class AssistantElementTools {

    private static final Logger log = LoggerFactory.getLogger(AssistantElementTools.class);

    private final IEditingContextSearchService editingContextSearchService;

    private final IRepresentationSearchService representationSearchService;

    //getConnectorToolsEventHandler
    private final GetPaletteEventHandler getPaletteEventHandler;

    private final GetConnectorToolsEventHandler getConnectorToolsEventHandler;

    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    public AssistantElementTools(IRepresentationSearchService representationSearchService,
                                 IEditingContextSearchService editingContextSearchService,
                                 @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                                 GetPaletteEventHandler paletteEventHandler,
                                 GetConnectorToolsEventHandler getConnectorToolsEventHandler) {
        this.editingContextSearchService = editingContextSearchService;
        this.representationSearchService = Objects.requireNonNull(representationSearchService);
        this.getPaletteEventHandler = Objects.requireNonNull(paletteEventHandler);
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
        this.getConnectorToolsEventHandler = Objects.requireNonNull(getConnectorToolsEventHandler);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                           PRIVATE GETTERS AND METHODS
    // ---------------------------------------------------------------------------------------------------------------

    private IEditingContext getEditingContext(IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            Optional<IEditingContext> optionalEditingContext = this.editingContextSearchService.findById(aiRequestInput.editingContextId());
            return optionalEditingContext.orElse(null);
        }
        throw new IllegalArgumentException("Input is not an AiRequestInput");
    }

    private Diagram getDiagram(IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            IEditingContext editingContext = this.getEditingContext(aiRequestInput);

            var optionalDiagram = this.representationSearchService.findById(editingContext, aiRequestInput.representationId(), Diagram.class);
            if (optionalDiagram.isPresent()) {
                return optionalDiagram.get();
            }
        }
        throw new IllegalArgumentException("Input is not an AiRequestInput");
    }

    private void rearrangeDiagramElements(IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            log.info("Rearranging elements for {}", aiRequestInput.representationId());

            LayoutDiagramInput layoutDiagramInput = new LayoutDiagramInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    aiRequestInput.diagramLayoutDataInput()
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(layoutDiagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(layoutDiagramInput));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                               DIAGRAM ELEMENTS GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a Map of existing Diagram Element IDs, with a focus on objects. The outer Map uses data types as keys, mapping to nested Maps where each element ID serves as a key and its corresponding type as the value. These element IDs form the foundation for the process, as newly created objects will be children of the selected diagram element.")
    public Map<String, Map<String, String>> getExistingDiagramObjectsIds(IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            Map<String, Map<String, String>> availableElements = new HashMap<>();
            Map<String, String> diagramRoot = new HashMap<>();
            Map<String, String> availableNodes = new HashMap<>();

            Diagram diagram = getDiagram(input);

            diagramRoot.put(aiRequestInput.representationId(), "Diagram's Root");

            for (Node node : diagram.getNodes()) {
                availableNodes.put(node.getId(), node.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=",""));
            }

            availableElements.put("Root", diagramRoot);
            availableElements.put("Objects", availableNodes);

            log.info("Retrieved available diagram objects: {}", availableElements);

            return availableElements;
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    @Tool("Use this tool only when not working with the root. Retrieve a Map of existing children of a Diagram Element. Each element ID serves as a key and its corresponding type as the value. These element IDs form the foundation for the process, as newly created objects will be children of the selected diagram element.")
    public Map<String, String> getExistingChildrenIdsInsideDiagramElement(String diagramElementId, IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            Map<String, String> availableChildNodes = new HashMap<>();

            Diagram diagram = getDiagram(input);

            Node diagramElement = diagram.getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), diagramElementId))
                    .findFirst()
                    .orElse(null);

            assert diagramElement != null;
            for (Node node : diagramElement.getChildNodes()) {
                availableChildNodes.put(node.getId(), node.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=",""));
            }

            log.info("Retrieved available object inside element {} : {}", diagramElementId, availableChildNodes);

            return availableChildNodes;
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }


    @Tool("Retrieve a Map of existing Diagram Element IDs, with a focus on edges. The outer Map uses data types as keys, mapping to nested Maps where the edge's source element Id serves as a key and the edge's target element Id as the value.")
    public Map<String, Map<String, String>> getExistingDiagramEdgesIds(IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            Map<String, Map<String, String>> availableEdges = new HashMap<>();
            Map<String, String> sourceAndTargetNodes = new HashMap<>();

            Diagram diagram = getDiagram(input);

            for (Edge edge : diagram.getEdges()) {
                sourceAndTargetNodes.put(edge.getSourceId(), edge.getTargetId());
                availableEdges.put(edge.getId(), sourceAndTargetNodes);
            }

            log.info("Retrieved available edges: {}", availableEdges);

            return availableEdges;
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @SystemMessage("You must use another tool to retrieve existing diagram element ids.")
    @Tool("Retrieve the list of available Objects to create organized as a Map. The Map uses tool IDs as keys and the object they create as values.")
    public Map<String, String> getObjectCreationTools(@P("The ai request input") IInput input, @P("The diagram object id. Use another tool to retrieve the existing ones.") String diagramObjectId) throws Exception {
        log.info("Retrieving object creation tools");

        Map<String, String> creationTools = new HashMap<>();

        if (input instanceof AiRequestInput aiRequestInput) {
            Diagram diagram = getDiagram(input);

                GetPaletteInput paletteInput = new GetPaletteInput(
                        UUID.randomUUID(),
                        aiRequestInput.editingContextId(),
                        aiRequestInput.representationId(),
                        diagramObjectId
                );

                Sinks.One<IPayload> payloadSink = Sinks.one();

                this.getPaletteEventHandler.handle(payloadSink, Sinks.many().unicast().onBackpressureBuffer(), this.getEditingContext(aiRequestInput), new DiagramContext(diagram), paletteInput);

                Mono<IPayload> payloadMono = payloadSink.asMono();

                payloadMono.subscribe(payload -> {
                    if (payload instanceof GetPaletteSuccessPayload getPaletteSuccessPayload) {
                        getPaletteSuccessPayload.palette().paletteEntries().stream()
                                .filter(ToolSection.class::isInstance)
                                .filter(toolSection -> Objects.equals(((ToolSection) toolSection).label(), "Creation Tools"))
                                .forEach(toolSection -> {
                                    Map<String, String> availableObjects = new HashMap<>();

                                    for (ITool tool : ((ToolSection) toolSection).tools()) {
                                        creationTools.put(tool.id(), tool.label());
                                    }
                                });
                    }
                }, throwable -> System.err.println("Failed to retrieve payload: " + throwable.getMessage()));
            }

        log.info("Available Tools : {}", creationTools.toString());

        return creationTools;
    }

    @SystemMessage("You must use another tool to retrieve existing diagram element ids.")
    @Tool("Retrieve the list of Linking Tools organized as a Map. The Map uses tool IDs as keys and their labels or functions as values.")
    public Map<String, String> getLinkTools(@P("The ai request input") IInput input, @P("The diagram object id that will serve as a source. Use another tool to retrieve the existing ones.") String sourceDiagramElementId, @P("The diagram object id that will serve as a target. Use another tool to retrieve the existing ones.") String targetDiagramElementId) throws Exception {
        log.info("Retrieving available connector tools");

        Map<String, String> availableConnectorTools = new HashMap<>();

        if (input instanceof AiRequestInput aiRequestInput) {
            Diagram diagram = getDiagram(input);

                GetConnectorToolsInput connectorToolsInput = new GetConnectorToolsInput(
                        UUID.randomUUID(),
                        aiRequestInput.editingContextId(),
                        aiRequestInput.representationId(),
                        sourceDiagramElementId,
                        targetDiagramElementId
                );

                Sinks.One<IPayload> payloadSink = Sinks.one();

                this.getConnectorToolsEventHandler.handle(payloadSink, Sinks.many().unicast().onBackpressureBuffer(), this.getEditingContext(aiRequestInput), new DiagramContext(diagram), connectorToolsInput);

                Mono<IPayload> payloadMono = payloadSink.asMono();

                payloadMono.subscribe(payload -> {
                    if (payload instanceof GetConnectorToolsSuccessPayload getConnectorToolsSuccessPayload) {
                        getConnectorToolsSuccessPayload.connectorTools()
                                .forEach(tool -> availableConnectorTools.put(tool.getId(), tool.getLabel()));
                    }
                }, throwable -> System.err.println("Failed to retrieve payload: " + throwable.getMessage()));
            }

        log.info("Available Connector Tools : {}", availableConnectorTools.values());

        return availableConnectorTools;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Execute a specific Object Tool based on its unique ID.")
    public String executeObjectTool(String toolId, String diagramElementId, @P("The ai request input.") IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            IEditingContext editingContext = getEditingContext(aiRequestInput);
            String representationId = aiRequestInput.representationId();

            assert editingContext != null;
            InvokeSingleClickOnDiagramElementToolInput diagramInput = new InvokeSingleClickOnDiagramElementToolInput(
                    UUID.randomUUID(),
                    editingContext.getId(),
                    representationId,
                    diagramElementId,
                    toolId,
                    0.0,
                    0.0,
                    List.of()
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            //rearrangeDiagramElements(aiRequestInput);
            return "Success";
        }
        return "Failure";
    }

    @Tool("Execute a specific Link Tool based on its unique ID, and a source and target diagram object Id.")
    public String executeLinkTool(String toolId, String sourceDiagramElementId, String targetDiagramElementId, @P("The ai request input.") IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            IEditingContext editingContext = getEditingContext(aiRequestInput);
            String representationId = aiRequestInput.representationId();

            assert editingContext != null;
            InvokeSingleClickOnTwoDiagramElementsToolInput diagramInput = new InvokeSingleClickOnTwoDiagramElementsToolInput(
                    UUID.randomUUID(),
                    editingContext.getId(),
                    representationId,
                    sourceDiagramElementId,
                    targetDiagramElementId,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    toolId,
                    List.of()
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            //rearrangeDiagramElements(aiRequestInput);
            return "Success";
        }
        return "Failure";
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT TOOLS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Edit the label of a diagram's object.")
    public String editObjectLabel(@P("The object's label Id to edit.") String diagramElementId, String newText, IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            Diagram diagram = this.getDiagram(aiRequestInput);

            String labelId = diagram.getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), diagramElementId))
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
                    newText
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            return "Success";
        }
        return "Failure";
    }

    @Tool("Edit the label of an object's child.")
    public String editChildLabel(String parentDiagramElementId, String childDiagramElementId, String newText, IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            Diagram diagram = this.getDiagram(aiRequestInput);

            String labelId = diagram.getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), parentDiagramElementId))
                    .map(node -> {
                        String childLabelId = node.getChildNodes().stream()
                                .filter(childNode -> Objects.equals(childNode.getId(), childDiagramElementId))
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
                    newText
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            return "Success";
        }
        return "Failure";
    }
}

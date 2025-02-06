package org.eclipse.sirius.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.util.PairDiagramElement;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.collaborative.diagrams.dto.*;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetConnectorToolsEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.diagrams.Edge;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiLinkTools extends AiTools {
    public AiLinkTools(IRepresentationSearchService representationSearchService,
                       IEditingContextSearchService editingContextSearchService,
                       @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                       GetConnectorToolsEventHandler getConnectorToolsEventHandler) {
        super(representationSearchService, editingContextSearchService, editingContextEventProcessorRegistry, getConnectorToolsEventHandler);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                               DIAGRAM ELEMENTS GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a Map of existing Links IDs structured as: [{link id : { source id, target id }}]")
    public Map<String, Map<String, String>> getExistingDiagramLinksIds() throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            Map<String, Map<String, String>> availableLinks = new HashMap<>();
            Map<String, String> sourceAndTargetNodes = new HashMap<>();

            this.diagram = this.getDiagram(aiRequestInput);

            for (Edge edge : this.diagram.getEdges()) {
                sourceAndTargetNodes.put(UUIDConverter.compress(edge.getSourceId()), UUIDConverter.compress(edge.getTargetId()));
                availableLinks.put(UUIDConverter.compress(edge.getId()), sourceAndTargetNodes);
            }

            return availableLinks;
        }
        throw new Exception("The input is not of type AiRequestInput.");
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL GETTERS
    // ---------------------------------------------------------------------------------------------------------------

//    @SystemMessage("You must use another tool to retrieve existing diagram element ids.")
    @Tool("Retrieve a list of available tools for linking objects together, structured as {link name, tool id}")
    public List<PairDiagramElement> getLinkTools(@P("The diagram object id that will serve as a source. Use another tool to retrieve the existing ones.") String sourceDiagramElementId, @P("The diagram object id that will serve as a target. Use another tool to retrieve the existing ones.") String targetDiagramElementId) throws Exception {
        List<PairDiagramElement> connectorTools = new ArrayList<>();

        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            GetConnectorToolsInput connectorToolsInput = new GetConnectorToolsInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    UUIDConverter.decompress(sourceDiagramElementId).toString(),
                    UUIDConverter.decompress(targetDiagramElementId).toString()
            );

            Sinks.One<IPayload> payloadSink = Sinks.one();

            if (this.editingContext == null) {
                this.editingContext = this.getEditingContext(aiRequestInput);
            }

            this.diagramEventHandler.handle(payloadSink, Sinks.many().unicast().onBackpressureBuffer(), this.editingContext, new DiagramContext(this.diagram), connectorToolsInput);

            Mono<IPayload> payloadMono = payloadSink.asMono();

            payloadMono.subscribe(payload -> {
                if (payload instanceof GetConnectorToolsSuccessPayload getConnectorToolsSuccessPayload) {
                    getConnectorToolsSuccessPayload.connectorTools()
                            .forEach(tool -> connectorTools.add(new PairDiagramElement(tool.getLabel(), UUIDConverter.compress(tool.getId()))));
                }
            }, throwable -> System.err.println("Failed to retrieve payload: " + throwable.getMessage()));
        }

        return connectorTools;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Perform the linking operation. Returns the new link's id.")
    public String executeLinkTool(@P("The id of the operation to execute.") String linkToolId, @P("The id of the source object.") String sourceDiagramElementId, @P("The id of the target object.") String targetDiagramElementId) throws Exception {
        AtomicReference<String> newLinkId = new AtomicReference<>("Failed to create link.");

        if (this.input instanceof AiRequestInput aiRequestInput) {
            InvokeSingleClickOnTwoDiagramElementsToolInput diagramInput = new InvokeSingleClickOnTwoDiagramElementsToolInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    sourceDiagramElementId,
                    targetDiagramElementId,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    linkToolId,
                    List.of()
            );

            AtomicReference<Mono<IPayload>> payload = new AtomicReference<>();

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> payload.set(processor.handle(diagramInput)));

            payload.get().subscribe(invokePayload -> {
                if (invokePayload instanceof InvokeSingleClickOnTwoDiagramElementsToolSuccessPayload successPayload) {
                    newLinkId.set(successPayload.newSelection().getEntries().get(0).getId());
                }
            });
        }
        return UUIDConverter.compress(newLinkId.get());
    }
}

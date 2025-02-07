package org.eclipse.sirius.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.util.PairDiagramElement;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.collaborative.diagrams.dto.*;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetConnectorToolsSuccessPayload;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetConnectorToolsEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.diagrams.tools.ITool;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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

    @Tool("Retrieve a Map of existing Links IDs structured as: {link id, { source id, target id }}")
    public Map<String, Map<String, String>> getExistingDiagramLinksIds() throws Exception {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            var availableLinks = new HashMap<String, Map<String, String>>();
            var sourceAndTargetNodes = new HashMap<String, String>();

            this.diagram = this.getDiagram(aiRequestInput);

            for (var edge : this.diagram.getEdges()) {
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

    @Tool("Retrieve a list of available operations for linking objects together, structured as {link name, operation id}")
    public List<PairDiagramElement> getLinkOperations(@P("The object id that will serve as source.") String sourceDiagramElementId, @P("The object id that will serve as target.") String targetDiagramElementId) throws Exception {
        var connectorTools = new ArrayList<PairDiagramElement>();

        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.diagram = this.getDiagram(aiRequestInput);
            }

            var connectorToolsInput = new GetConnectorToolsInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    UUIDConverter.decompress(sourceDiagramElementId).toString(),
                    UUIDConverter.decompress(targetDiagramElementId).toString()
            );

            var payload = new AtomicReference<Mono<IPayload>>();

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(connectorToolsInput.editingContextId())
                    .ifPresent(processor -> payload.set(processor.handle(connectorToolsInput)));

            payload.get().subscribe(invokePayload -> {
                if (invokePayload instanceof GetConnectorToolsSuccessPayload successPayload) {
                    for (ITool linkTool : successPayload.connectorTools()) {
                        connectorTools.add(new PairDiagramElement(linkTool.getLabel(), UUIDConverter.compress(linkTool.getId())));
                    }
                }
            });
        }

        if (connectorTools.isEmpty()) {
            connectorTools.add(new PairDiagramElement("Can not link these objects together, try something else.", null));
        }

        return connectorTools;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Perform the linking operation. Returns the new link's id.")
    public String linkObjects(@P("The id of the operation to perform.") String linkOperationId, @P("The id of the source object.") String sourceObjectId, @P("The id of the target object.") String targetObjectId) throws Exception {
        var newLinkId = new AtomicReference<>("Failed to create link.");

        if (this.input instanceof AiRequestInput aiRequestInput) {
            var diagramInput = new InvokeSingleClickOnTwoDiagramElementsToolInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    sourceObjectId,
                    targetObjectId,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    linkOperationId,
                    List.of()
            );

            var payload = new AtomicReference<Mono<IPayload>>();

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

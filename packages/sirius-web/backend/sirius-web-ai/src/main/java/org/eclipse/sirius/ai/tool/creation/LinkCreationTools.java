package org.eclipse.sirius.ai.tool.creation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.tool.AiTools;
import org.eclipse.sirius.ai.util.PairDiagramElement;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.DiagramContext;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetConnectorToolsInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetConnectorToolsSuccessPayload;
import org.eclipse.sirius.components.collaborative.diagrams.dto.InvokeSingleClickOnTwoDiagramElementsToolInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.InvokeSingleClickOnTwoDiagramElementsToolSuccessPayload;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetConnectorToolsEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IPayload;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LinkCreationTools extends AiTools {
    public LinkCreationTools(IRepresentationSearchService representationSearchService,
                             IEditingContextSearchService editingContextSearchService,
                             @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                             GetConnectorToolsEventHandler getConnectorToolsEventHandler) {
        super(representationSearchService, editingContextSearchService, editingContextEventProcessorRegistry, getConnectorToolsEventHandler);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                CREATION LINK TOOL GETTER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a list of available operations for linking objects together, structured as {link name, operation id}. Links can be directed, if there are no available operations, try switching source and target.")
    public List<PairDiagramElement> getLinkOperations(@P("The object id that will serve as source.") String sourceObjectId, @P("The object id that will serve as target.") String targetObjectId) {
        var linkOperations = new ArrayList<PairDiagramElement>();

        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.refreshDiagram();
            }

            var connectorToolsInput = new GetConnectorToolsInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    UUIDConverter.decompress(sourceObjectId).toString(),
                    UUIDConverter.decompress(targetObjectId).toString()
            );

            Sinks.One<IPayload> payloadSink = Sinks.one();

            if (this.editingContext == null) {
                this.editingContext = this.getEditingContext();
            }

            this.diagramEventHandler.handle(payloadSink, Sinks.many().unicast().onBackpressureBuffer(), this.editingContext, new DiagramContext(this.diagram), connectorToolsInput);

            var payloadMono = payloadSink.asMono();

            payloadMono.subscribe(payload -> {
                if (payload instanceof GetConnectorToolsSuccessPayload getConnectorToolsSuccessPayload) {
                    getConnectorToolsSuccessPayload.connectorTools()
                            .forEach(tool -> linkOperations.add(new PairDiagramElement(tool.getLabel(), UUIDConverter.compress(tool.getId()))));

                }
            }, throwable -> System.err.println("Failed to retrieve payload: " + throwable.getMessage()));
        }

        if (linkOperations.isEmpty()) {
            linkOperations.add(new PairDiagramElement("Can not link these objects together, try something else.", null));
        }

        return linkOperations;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Perform the linking operation. Returns the new link's id.")
    public String linkObjects(@P("The id of the operation to perform.") String linkOperationId, @P("The id of the source object.") String sourceObjectId, @P("The id of the target object.") String targetObjectId) {
        var newLinkId = new AtomicReference<>("Failed to create link.");
        if (this.input instanceof AiRequestInput aiRequestInput) {
            var diagramInput = new InvokeSingleClickOnTwoDiagramElementsToolInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    UUIDConverter.decompress(sourceObjectId).toString(),
                    UUIDConverter.decompress(targetObjectId).toString(),
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    UUIDConverter.decompress(linkOperationId).toString(),
                    List.of()
            );

            this.refreshDiagram();
            var alreadyExistingLinks = diagram.getEdges();

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            this.refreshDiagram();
            var newLink = this.diagram.getEdges().stream().filter(link -> !alreadyExistingLinks.contains(link)).findFirst();
            newLink.ifPresent(link -> newLinkId.set(link.getTargetObjectId()));
        }
        return UUIDConverter.compress(newLinkId.get());
    }
}

package org.eclipse.sirius.web.ai.tool.creation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.util.PairDiagramElement;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetConnectorToolsInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetConnectorToolsSuccessPayload;
import org.eclipse.sirius.components.collaborative.diagrams.dto.InvokeSingleClickOnTwoDiagramElementsToolInput;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetConnectorToolsEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LinkCreationTools implements AiTool {
    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final GetConnectorToolsEventHandler getConnectorToolsEventHandler;

    private final AiToolService aiToolService;

    public LinkCreationTools(AiToolService aiToolService,
                             @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                             GetConnectorToolsEventHandler getConnectorToolsEventHandler) {
        this.aiToolService = aiToolService;
        this.editingContextEventProcessorRegistry = editingContextEventProcessorRegistry;
        this.getConnectorToolsEventHandler = getConnectorToolsEventHandler;
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                CREATION LINK TOOL GETTER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a list of available operations for linking objects together, structured as {link name, operation id}. Links can be directed, if there are no available operations, try switching source and target. This does not create the link.")
    public List<PairDiagramElement> getLinkOperations(@P("The object id that will serve as source.") String sourceObjectId, @P("The object id that will serve as target.") String targetObjectId) {
        this.aiToolService.refreshDiagram();
        var linkOperations = new ArrayList<PairDiagramElement>();

        var sourceNode = this.aiToolService.findNode(UUIDConverter.decompress(sourceObjectId).toString());
        var targetNode = this.aiToolService.findNode(UUIDConverter.decompress(targetObjectId).toString());

        assert sourceNode != null && targetNode != null;
        var connectorInput = new GetConnectorToolsInput(
                UUID.randomUUID(),
                this.aiToolService.getEditingContextId(),
                this.aiToolService.getRepresentationId(),
                sourceNode.getId(),
                targetNode.getId()
        );

        var payload = new AtomicReference<Mono<IPayload>>();

        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(connectorInput.editingContextId())
                .ifPresent(processor -> payload.set(processor.handle(connectorInput)));

        payload.get().subscribe(invokePayload -> {
            if (invokePayload instanceof GetConnectorToolsSuccessPayload successPayload) {
                successPayload.connectorTools()
                        .forEach(tool -> {
                            linkOperations.add(new PairDiagramElement(tool.getLabel(), UUIDConverter.compress(tool.getId())));
                        });
            }
        });

        if (linkOperations.isEmpty()) {
            linkOperations.add(new PairDiagramElement("Can not link these objects together, try something else.", null));
        }

        return linkOperations;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Perform the linking operation, thus creates a new link. Returns the new link's id.")
    public String linkObjects(@P("The id of the operation to perform.") String linkOperationId, @P("The id of the source object.") String sourceObjectId, @P("The id of the target object.") String targetObjectId) {
        var diagramInput = new InvokeSingleClickOnTwoDiagramElementsToolInput(
                UUID.randomUUID(),
                this.aiToolService.getEditingContextId(),
                this.aiToolService.getRepresentationId(),
                UUIDConverter.decompress(sourceObjectId).toString(),
                UUIDConverter.decompress(targetObjectId).toString(),
                0.0,
                0.0,
                0.0,
                0.0,
                UUIDConverter.decompress(linkOperationId).toString(),
                List.of()
        );

        this.aiToolService.refreshDiagram();
        var alreadyExistingLinks = this.aiToolService.getDiagram().getEdges();

        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                .ifPresent(processor -> processor.handle(diagramInput));

        this.aiToolService.refreshDiagram();

        var newLinkId = "Failed to create new link";
        for (var newLink : this.aiToolService.getDiagram().getEdges()) {
            if (!alreadyExistingLinks.contains(newLink)) {
                newLinkId = newLink.getId();
                break;
            }
        }

        return UUIDConverter.compress(newLinkId);
    }
}

package org.eclipse.sirius.web.ai.tool.creation;

import org.eclipse.sirius.web.ai.tool.service.AiDiagramService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.dto.PairDiagramElement;
import org.eclipse.sirius.web.ai.codec.UUIDCodec;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetConnectorToolsInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetConnectorToolsSuccessPayload;
import org.eclipse.sirius.components.collaborative.diagrams.dto.InvokeSingleClickOnTwoDiagramElementsToolInput;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LinkCreationTools implements AiTool {
    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final AiDiagramService aiDiagramService;

    private final List<String> linkIds = new ArrayList<>();

    public LinkCreationTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                             AiDiagramService aiDiagramService) {
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
        this.aiDiagramService = Objects.requireNonNull(aiDiagramService);
    }

    @Override
    public void setInput(IInput input) {
        this.aiDiagramService.setInput(input);
    }

    public List<String> getLinkIds() {
        return linkIds;
    }

    public void clearLinkIds() {
        this.linkIds.clear();
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                CREATION LINK TOOL GETTER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool(description = "Retrieve a list of available operations for linking objects together, structured as {link name, operation id}. Links can be directed, if there are no available operations, try switching source and target. This does not create the link.")
    public List<PairDiagramElement> getAvailableLinkOperations(@ToolParam(description = "The object id that will serve as source.") String sourceObjectId, @ToolParam(description = "The object id that will serve as target.") String targetObjectId) {
        UUID decompressedSourceId;
        UUID decompressedTargetId;

        try {
            decompressedSourceId = new UUIDCodec().decompress(sourceObjectId);
            decompressedTargetId = new UUIDCodec().decompress(targetObjectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Source or target object id is not in the correct format.");
        }

        this.aiDiagramService.refreshDiagram();
        var linkOperations = new ArrayList<PairDiagramElement>();

        var sourceNode = this.aiDiagramService.findNode(decompressedSourceId.toString());
        var targetNode = this.aiDiagramService.findNode(decompressedTargetId.toString());

        Objects.requireNonNull(sourceNode);
        Objects.requireNonNull(targetNode);
        var connectorInput = new GetConnectorToolsInput(
                UUID.randomUUID(),
                this.aiDiagramService.getEditingContextId(),
                this.aiDiagramService.getDiagramId(),
                sourceNode.getId(),
                targetNode.getId()
        );

        var payload = new AtomicReference<Mono<IPayload>>();

        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(connectorInput.editingContextId())
                .ifPresent(processor -> payload.set(processor.handle(connectorInput)));

        payload.get().subscribe(invokePayload -> {
            if (invokePayload instanceof GetConnectorToolsSuccessPayload successPayload) {
                successPayload.connectorTools()
                        .forEach(tool ->
                            linkOperations.add(new PairDiagramElement(tool.getLabel(), new UUIDCodec().compress(tool.getId())))
                        );
            }
        });

        if (linkOperations.isEmpty()) {
            linkOperations.add(new PairDiagramElement("Can not link these objects together, try something else.", null));
        }

        return linkOperations;
    }

    @Tool(description = "Call this tool when linking two objects together is absolutely impossible in any way, shape or form. If there is a way to link them (even indirectly), this tool should not be called.")
    public void unableToLink(@ToolParam(description = "The id of the source object.") String sourceObjectId, @ToolParam(description = "The id of the target object.") String targetObjectId) {
        this.linkIds.add("Not able to link object " + sourceObjectId + " to " + targetObjectId + ". Try something else, maybe the objects are not compatible.");
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool(description = "Perform the linking operation, thus creates a new link. Returns the new link's id.")
    public String linkObjects(@ToolParam(description = "The id of the operation to perform.") String linkOperationId, @ToolParam(description = "The id of the source object.") String sourceObjectId, @ToolParam(description = "The id of the target object.") String targetObjectId) {
        UUID decompressedOperationId;
        UUID decompressedSourceId;
        UUID decompressedTargetId;

        try {
            decompressedOperationId = new UUIDCodec().decompress(linkOperationId);
            decompressedSourceId = new UUIDCodec().decompress(sourceObjectId);
            decompressedTargetId = new UUIDCodec().decompress(targetObjectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Operation, source or target object id is not in the correct format.");
        }

        var diagramInput = new InvokeSingleClickOnTwoDiagramElementsToolInput(
                UUID.randomUUID(),
                this.aiDiagramService.getEditingContextId(),
                this.aiDiagramService.getDiagramId(),
                decompressedSourceId.toString(),
                decompressedTargetId.toString(),
                0.0,
                0.0,
                0.0,
                0.0,
                decompressedOperationId.toString(),
                List.of()
        );

        var alreadyExistingLinks = this.aiDiagramService.getDiagram().getEdges();

        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                .ifPresent(processor -> processor.handle(diagramInput));

        this.aiDiagramService.refreshDiagram();

        String newLinkId = null;
        for (var newLink : this.aiDiagramService.getDiagram().getEdges()) {
            if (!alreadyExistingLinks.contains(newLink)) {
                newLinkId = newLink.getId();
                break;
            }
        }

        if (newLinkId == null) {
            return "Failed to create new Link.";
        }

        var result = "Link linking " + sourceObjectId + " and " + targetObjectId + " created with id: " + new UUIDCodec().compress(newLinkId);
        this.linkIds.add(result);

        return result;
    }
}

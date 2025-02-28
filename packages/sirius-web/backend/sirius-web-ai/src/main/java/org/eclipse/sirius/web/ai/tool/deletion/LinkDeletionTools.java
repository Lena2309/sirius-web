package org.eclipse.sirius.web.ai.tool.deletion;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.diagrams.dto.DeleteFromDiagramInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.DeletionPolicy;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetConnectorToolsEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class LinkDeletionTools implements AiTool {
    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final AiToolService aiToolService;

    public LinkDeletionTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                             AiToolService aiToolService) {
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
        this.aiToolService = Objects.requireNonNull(aiToolService);
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Delete the link from the diagram.")
    public String deleteLink(@P("The id of the link to delete.") String linkId) {
        UUID decompressedLinkId;

        try {
            decompressedLinkId = UUIDConverter.decompress(linkId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Link id is not in the correct format.");
        }

        var deleteInput = new DeleteFromDiagramInput(
                UUID.randomUUID(),
                this.aiToolService.getEditingContextId(),
                this.aiToolService.getRepresentationId(),
                List.of(),
                List.of(decompressedLinkId.toString()),
                DeletionPolicy.SEMANTIC
        );

        var link = this.aiToolService.findEdge(UUIDConverter.decompress(linkId).toString());

        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(deleteInput.editingContextId())
                .ifPresent(processor -> processor.handle(deleteInput));

        this.aiToolService.refreshDiagram();
        if (this.aiToolService.getDiagram().getEdges().contains(link)) {
            return "Failure";
        } else {
            return "Success";
        }
    }
}

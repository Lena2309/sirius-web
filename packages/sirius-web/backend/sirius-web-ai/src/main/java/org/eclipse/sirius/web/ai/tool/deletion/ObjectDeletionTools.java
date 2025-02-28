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
public class ObjectDeletionTools implements AiTool {
    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final AiToolService aiToolService;

    public ObjectDeletionTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                               AiToolService aiToolService) {
        this.aiToolService = Objects.requireNonNull(aiToolService);
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  TOOL EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Delete the object from the diagram.")
    public String deleteObject(@P("The id of the object to delete.") String objectId) {
        UUID decompressedObjectId;

        try {
            decompressedObjectId = UUIDConverter.decompress(objectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Object id is not in the correct format.");
        }

        var deleteInput = new DeleteFromDiagramInput(
                UUID.randomUUID(),
                this.aiToolService.getEditingContextId(),
                this.aiToolService.getRepresentationId(),
                List.of(decompressedObjectId.toString()),
                List.of(),
                DeletionPolicy.SEMANTIC
        );

        var node = this.aiToolService.findNode(UUIDConverter.decompress(objectId).toString());

        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(deleteInput.editingContextId())
                .ifPresent(processor -> processor.handle(deleteInput));

        this.aiToolService.refreshDiagram();
        if (this.aiToolService.getDiagram().getNodes().contains(node)) {
            return "Failure";
        } else {
            return "Success";
        }
    }
}

package org.eclipse.sirius.web.ai.tool.deletion;


import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.core.api.SuccessPayload;
import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.diagrams.dto.DeleteFromDiagramInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.DeletionPolicy;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

    @Tool(description = "Delete the object from the diagram.")
    public String deleteObject(@ToolParam(description = "The id of the object to delete.") String objectId) {
        UUID decompressedObjectId;

        try {
            decompressedObjectId = UUIDConverter.decompress(objectId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Object id is not in the correct format.");
        }

        var deleteInput = new DeleteFromDiagramInput(
                UUID.randomUUID(),
                this.aiToolService.getEditingContextId(),
                this.aiToolService.getDiagramId(),
                List.of(decompressedObjectId.toString()),
                List.of(),
                DeletionPolicy.SEMANTIC
        );

        var payload = new AtomicReference<Mono<IPayload>>();
        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(deleteInput.editingContextId())
                .ifPresent(processor -> payload.set(processor.handle(deleteInput)));

        var output = new AtomicReference<String>();
        payload.get().subscribe(invokePayload -> {
            if (invokePayload instanceof SuccessPayload) {
                output.set("Object successfully deleted.");
            } else {
                output.set("Object could not be deleted.");
            }
        });

        return output.get();
    }
}

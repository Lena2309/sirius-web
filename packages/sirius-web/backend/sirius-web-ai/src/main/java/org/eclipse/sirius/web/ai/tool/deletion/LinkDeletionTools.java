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

    @Tool(description = "Delete the link from the diagram.")
    public String deleteLink(@ToolParam(description = "The id of the link to delete.") String linkId) {
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

        var payload = new AtomicReference<Mono<IPayload>>();

        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(deleteInput.editingContextId())
                .ifPresent(processor -> payload.set(processor.handle(deleteInput)));

        var output = new AtomicReference<String>();
        payload.get().subscribe(invokePayload -> {
            if (invokePayload instanceof SuccessPayload) {
                output.set("Link successfully deleted.");
            } else {
                output.set("Link could not be deleted.");
            }
        });

        return output.get();
    }
}

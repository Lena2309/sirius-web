package org.eclipse.sirius.ai.tool.creation;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.service.AiToolService;
import org.eclipse.sirius.ai.tool.AiTool;
import org.eclipse.sirius.ai.util.PairDiagramElement;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetPaletteInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetPaletteSuccessPayload;
import org.eclipse.sirius.components.collaborative.diagrams.dto.InvokeSingleClickOnDiagramElementToolInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.ToolSection;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.diagrams.Node;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;


@Service
public class ObjectCreationTools implements AiTool {
    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final AiToolService aiToolService;

    public ObjectCreationTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                               AiToolService aiToolService) {
        this.editingContextEventProcessorRegistry = editingContextEventProcessorRegistry;
        this.aiToolService = aiToolService;
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                          OBJECT CREATION OPERATION GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve the list of available creation operations at root structured as {type of the object to create, operation id}")
    public List<PairDiagramElement> getAvailableRootObjectCreationOperations() {
        this.aiToolService.refreshDiagram();

            var paletteInput = new GetPaletteInput(
                    UUID.randomUUID(),
                    this.aiToolService.getEditingContextId(),
                    this.aiToolService.getRepresentationId(),
                    this.aiToolService.getRepresentationId()
            );

            return getCreationTools(paletteInput);
    }

    @Tool("Retrieve the list of available child creation operations structured as {type of the child to create, operation id}")
    public List<PairDiagramElement> getAvailableChildCreationOperations(@P("The parent id.") String parentId) {
        this.aiToolService.refreshDiagram();

            var paletteInput = new GetPaletteInput(
                    UUID.randomUUID(),
                    this.aiToolService.getEditingContextId(),
                    this.aiToolService.getRepresentationId(),
                    UUIDConverter.decompress(parentId).toString()
            );

            return getCreationTools(paletteInput);
    }

    private List<PairDiagramElement> getCreationTools(IInput input) {
        if (input instanceof GetPaletteInput paletteInput) {
            var creationTools = new ArrayList<PairDiagramElement>();
            var payload = new AtomicReference<Mono<IPayload>>();

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(paletteInput.editingContextId())
                    .ifPresent(processor -> payload.set(processor.handle(paletteInput)));

            payload.get().subscribe(invokePayload -> {
                if (invokePayload instanceof GetPaletteSuccessPayload successPayload) {
                    successPayload.palette().paletteEntries().stream()
                            .filter(ToolSection.class::isInstance)
                            .filter(toolSection -> ((ToolSection) toolSection).label().contains("Creation"))
                            .forEach(toolSection -> {
                                for (var tool : ((ToolSection) toolSection).tools()) {
                                    creationTools.add(new PairDiagramElement(tool.label(), UUIDConverter.compress(tool.id())));
                                }
                            });
                }
            });

            return creationTools;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  OPERATION EXECUTIONER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Perform the creation operation at root. Returns the new object's id. The id should not be modified.")
    public String createObjectAtRoot(@P("The id of the operation to execute.") String operationId) {
            var diagramInput = new InvokeSingleClickOnDiagramElementToolInput(
                    UUID.randomUUID(),
                    this.aiToolService.getEditingContextId(),
                    this.aiToolService.getRepresentationId(),
                    this.aiToolService.getRepresentationId(),
                    UUIDConverter.decompress(operationId).toString(),
                    0.0,
                    0.0,
                    List.of()
            );

        this.aiToolService.refreshDiagram();
            var alreadyExistingObjects = new ArrayList<>();

            for (Node node : this.aiToolService.getDiagram().getNodes()) {
                alreadyExistingObjects.add(node.getId());
            }

            var newObjectId = "Failed to create object.";

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

        this.aiToolService.refreshDiagram();

            for (Node node : this.aiToolService.getDiagram().getNodes()) {
                if (!alreadyExistingObjects.contains(node.getId())) {
                    newObjectId = node.getId();
                }
            }

            return UUIDConverter.compress(newObjectId);
    }

    @Tool("Perform the creation operation. Returns the new child's id. The id should not be modified.")
    public String createChild(@P("The id of the operation to perform.") String operationId, @P("The parent's id .") String parentId) {

            var diagramInput = new InvokeSingleClickOnDiagramElementToolInput(
                    UUID.randomUUID(),
                    this.aiToolService.getEditingContextId(),
                    this.aiToolService.getRepresentationId(),
                    UUIDConverter.decompress(parentId).toString(),
                    UUIDConverter.decompress(operationId).toString(),
                    0.0,
                    0.0,
                    List.of()
            );

        this.aiToolService.refreshDiagram();

        var parentNode = this.aiToolService.findNode(UUIDConverter.decompress(parentId).toString());
        var alreadyExistingChildren = parentNode.getChildNodes();

        var newChildId = "Failed to create object.";

        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                .ifPresent(processor -> processor.handle(diagramInput));

        this.aiToolService.refreshDiagram();

        parentNode = this.aiToolService.findNode(UUIDConverter.decompress(parentId).toString());

        var newChild = parentNode.getChildNodes().stream()
                .filter(child -> !alreadyExistingChildren.contains(child))
                .findFirst();

        assert newChild.isPresent();
        newChildId = newChild.get().getId();

        for (Node child : parentNode.getChildNodes()) {
            if (!alreadyExistingChildren.contains(child)) {
                newChildId = child.getId();
            }
        }

        return UUIDConverter.compress(newChildId);
    }
}

package org.eclipse.sirius.web.ai.tool.service;

import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.dto.InvokeSingleClickOnDiagramElementToolSuccessPayload;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.web.ai.dto.AiRequestInput;
import org.eclipse.sirius.web.ai.codec.UUIDCodec;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiDiagramService {
    private final IRepresentationSearchService representationSearchService;

    private final IEditingContextSearchService editingContextSearchService;

    private IInput input = null;

    private Diagram diagram = null;

    public AiDiagramService(IRepresentationSearchService representationSearchService,
                            IEditingContextSearchService editingContextSearchService) {
        this.representationSearchService = representationSearchService;
        this.editingContextSearchService = editingContextSearchService;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  GETTERS AND SETTERS
    // ---------------------------------------------------------------------------------------------------------------

    public void setInput(IInput input) {
        this.input = input;
    }

    public Optional<IEditingContext> getEditingContext() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            return this.editingContextSearchService.findById(aiRequestInput.editingContextId());
        }
        return Optional.empty();
    }

    public String getEditingContextId() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            return aiRequestInput.editingContextId();
        }
        return null;
    }

    public Diagram getDiagram() {
        return this.diagram;
    }

    public String getDiagramId() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            return aiRequestInput.diagramId();
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  REFRESH METHODS
    // ---------------------------------------------------------------------------------------------------------------

    public void refreshDiagram() {
        if (this.input instanceof AiRequestInput aiRequestInput) {

            var optionalEditingContext = this.getEditingContext();
            assert optionalEditingContext.isPresent();
            var optionalDiagram = this.representationSearchService.findById(optionalEditingContext.get(), aiRequestInput.diagramId(), Diagram.class);
            optionalDiagram.ifPresent(diagram -> this.diagram = diagram);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  FIND DIAGRAM ELEMENT
    // ---------------------------------------------------------------------------------------------------------------

    public Edge findEdge(String edgeId) {
        for (var edge : this.diagram.getEdges()) {
            if (edge.getId().equals(edgeId)) {
                return edge;
            }
        }
        return null;
    }

    public Node findNode(String objectId) {
        for (var parent : this.diagram.getNodes()) {
            if (objectId.equals(parent.getId())) {
                return parent;
            }
            var child = findRecursiveNode(objectId, parent);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private Node findRecursiveNode(String objectId, Node parent) {
        for (var child : parent.getChildNodes()) {
            if (objectId.equals(child.getId())) {
                return child;
            } else if (!child.getChildNodes().isEmpty()) {
                var grandchild = findRecursiveNode(objectId, child);
                if (grandchild != null) {
                    return grandchild;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  CREATE DIAGRAM ELEMENT
    // ---------------------------------------------------------------------------------------------------------------

    public synchronized String createNewNode(EditingContextEventProcessorRegistry editingContextEventProcessorRegistry, IInput input, String editingContextId) {
        var alreadyExistingObjects = new ArrayList<>();

        for (var node : diagram.getNodes()) {
            alreadyExistingObjects.add(node.getId());
        }

        if (!callSuccessfullyEditingContextEventProcessor(editingContextEventProcessorRegistry, input, editingContextId)) {
            return null;
        }

        this.refreshDiagram();

        String newObjectId = null;
        for (var node : diagram.getNodes()) {
            if (!alreadyExistingObjects.contains(node.getId())) {
                newObjectId = node.getId();
                break;
            }
        }
        return newObjectId;
    }

    public synchronized String createNewChild(EditingContextEventProcessorRegistry editingContextEventProcessorRegistry, IInput input, String editingContextId, String parentId) {
        var alreadyExistingObjects = new ArrayList<>();

        for (var node : diagram.getNodes()) {
            alreadyExistingObjects.add(node.getId());
        }

        if (!callSuccessfullyEditingContextEventProcessor(editingContextEventProcessorRegistry, input, editingContextId)) {
            return null;
        }

        this.refreshDiagram();

        String newChildId = null;
        var parentNode = findNode(new UUIDCodec().decompress(parentId).toString());
        for (var child : parentNode.getChildNodes()) {
            if (!alreadyExistingObjects.contains(child.getId())) {
                newChildId = child.getId();
                break;
            }
        }
        return newChildId;
    }

    private boolean callSuccessfullyEditingContextEventProcessor(EditingContextEventProcessorRegistry editingContextEventProcessorRegistry, IInput input, String editingContextId) {
        var payload = new AtomicReference<Mono<IPayload>>();
        editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(editingContextId)
                .ifPresent(processor -> payload.set(processor.handle(input)));

        var objectCreated = new AtomicBoolean(false);
        payload.get().subscribe(invokePayload -> {
            if (invokePayload instanceof InvokeSingleClickOnDiagramElementToolSuccessPayload) {
                objectCreated.set(true);
            }
        });

        return objectCreated.get();
    }
}

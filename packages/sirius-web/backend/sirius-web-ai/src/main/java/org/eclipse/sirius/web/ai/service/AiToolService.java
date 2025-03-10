package org.eclipse.sirius.web.ai.service;

import org.eclipse.sirius.components.collaborative.diagrams.dto.InvokeSingleClickOnDiagramElementToolSuccessPayload;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.web.ai.dto.AiRequestInput;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.Node;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiToolService {

    private final IEditingContextSearchService editingContextSearchService;

    private final IRepresentationSearchService representationSearchService;

    private IInput input = null;

    private IEditingContext editingContext = null;

    private Diagram diagram = null;

    public AiToolService(IRepresentationSearchService representationSearchService,
                         IEditingContextSearchService editingContextSearchService) {
        this.editingContextSearchService = editingContextSearchService;
        this.representationSearchService = Objects.requireNonNull(representationSearchService);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                           PRIVATE GETTERS AND METHODS
    // ---------------------------------------------------------------------------------------------------------------

    public void setInput(IInput input) {
        this.input = input;
    }

    public String getRepresentationId() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            return aiRequestInput.representationId();
        }
        return null;
    }

    public String getEditingContextId() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            return aiRequestInput.editingContextId();
        }
        return null;
    }

    public IEditingContext getEditingContext() {
        this.refreshEditingContext();
        return this.editingContext;
    }

    public synchronized String createNewNode(EditingContextEventProcessorRegistry editingContextEventProcessorRegistry, IInput input, String editingContextId, String parentId) {
        String newObjectId = null;
        var alreadyExistingObjects = new ArrayList<>();

        if (parentId != null) {
            var parentNode = findNode(UUIDConverter.decompress(parentId).toString());
            for (var child : parentNode.getChildNodes()) {
                alreadyExistingObjects.add(child.getId());
            }
        } else {

            for (var node : diagram.getNodes()) {
                alreadyExistingObjects.add(node.getId());
            }
        }

        if (!callEditingContextEventProcessor(editingContextEventProcessorRegistry, input, editingContextId)) {
            return null;
        }

        this.refreshDiagram();

        if (parentId != null) {
            var parentNode = findNode(UUIDConverter.decompress(parentId).toString());
            for (var child : parentNode.getChildNodes()) {
                if (!alreadyExistingObjects.contains(child.getId())) {
                    newObjectId = child.getId();
                    break;
                }
            }
        } else {
            for (var node : diagram.getNodes()) {
                if (!alreadyExistingObjects.contains(node.getId())) {
                    newObjectId = node.getId();
                    break;
                }
            }
        }

        return newObjectId;
    }

    public synchronized String createNewChild(String parentId, EditingContextEventProcessorRegistry editingContextEventProcessorRegistry, IInput input, String editingContextId) {
        var parentNode = this.findNode(UUIDConverter.decompress(parentId).toString());
        var alreadyExistingChildren = new ArrayList<String>();

        for (var child : parentNode.getChildNodes()) {
            alreadyExistingChildren.add(child.getId());
        }

        if (!callEditingContextEventProcessor(editingContextEventProcessorRegistry, input, editingContextId)) {
            return "Failed to create child.";
        }

        this.refreshDiagram();
        parentNode = this.findNode(UUIDConverter.decompress(parentId).toString());

        String newChildId = null;
        for (var child : parentNode.getChildNodes()) {
            if (!alreadyExistingChildren.contains(child.getId())) {
                newChildId = child.getId();
                break;
            }
        }

        return newChildId;
    }

    private boolean callEditingContextEventProcessor(EditingContextEventProcessorRegistry editingContextEventProcessorRegistry, IInput input, String editingContextId) {
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


    private void refreshEditingContext() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            var optionalEditingContext = this.editingContextSearchService.findById(aiRequestInput.editingContextId());
            this.editingContext = optionalEditingContext.orElse(null);
        }
    }

    public Diagram getDiagram() {
        return this.diagram;
    }

    public void refreshDiagram() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.editingContext == null) {
                this.editingContext = this.getEditingContext();
            }

            var optionalDiagram = this.representationSearchService.findById(this.editingContext, aiRequestInput.representationId(), Diagram.class);
            optionalDiagram.ifPresent(diagram -> this.diagram = diagram);
        }
    }

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
}

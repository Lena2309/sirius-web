package org.eclipse.sirius.web.ai.service;

import org.eclipse.sirius.web.ai.dto.AiRequestInput;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.eclipse.sirius.components.diagrams.Edge;
import org.eclipse.sirius.components.diagrams.Node;
import org.springframework.stereotype.Service;

import java.util.Objects;

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
        if(this.editingContext == null) {
            if (this.input instanceof AiRequestInput aiRequestInput) {
                var optionalEditingContext = this.editingContextSearchService.findById(aiRequestInput.editingContextId());
                this.editingContext = optionalEditingContext.orElse(null);
            }
        }
        return this.editingContext;
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

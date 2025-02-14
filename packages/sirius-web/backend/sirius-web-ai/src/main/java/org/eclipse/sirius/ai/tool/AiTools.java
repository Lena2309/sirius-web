package org.eclipse.sirius.ai.tool;

import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.api.IDiagramEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.collaborative.forms.api.IFormEventHandler;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.Objects;

public abstract class AiTools {

    protected final IEditingContextSearchService editingContextSearchService;

    protected final IRepresentationSearchService representationSearchService;

    protected final IDiagramEventHandler diagramEventHandler;

    protected final  List<IFormEventHandler> formEventHandlers;

    protected final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    protected IInput input = null;

    protected IEditingContext editingContext = null;

    protected Diagram diagram = null;

    public AiTools(IRepresentationSearchService representationSearchService,
                   IEditingContextSearchService editingContextSearchService,
                   @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                   IDiagramEventHandler diagramEventHandler) {
        this.editingContextSearchService = editingContextSearchService;
        this.representationSearchService = Objects.requireNonNull(representationSearchService);
        this.diagramEventHandler = Objects.requireNonNull(diagramEventHandler);
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
        this.formEventHandlers = null;
    }

    public AiTools(IRepresentationSearchService representationSearchService,
                   IEditingContextSearchService editingContextSearchService,
                   @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                   List<IFormEventHandler> formEventHandlers) {
        this.editingContextSearchService = editingContextSearchService;
        this.representationSearchService = Objects.requireNonNull(representationSearchService);
        this.diagramEventHandler = null;
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
        this.formEventHandlers = Objects.requireNonNull(formEventHandlers);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                           PRIVATE GETTERS AND METHODS
    // ---------------------------------------------------------------------------------------------------------------

    public void setInput(IInput input) {
        this.input = input;
    }

    protected String getEditingContextId() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            return aiRequestInput.editingContextId();
        }
        return null;
    }
    protected IEditingContext getEditingContext() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            var optionalEditingContext = this.editingContextSearchService.findById(aiRequestInput.editingContextId());
            return optionalEditingContext.orElse(null);
        }
        return null;
    }

    protected void refreshDiagram() {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.editingContext == null) {
                this.editingContext = this.getEditingContext();
            }

            var optionalDiagram = this.representationSearchService.findById(this.editingContext, aiRequestInput.representationId(), Diagram.class);
            optionalDiagram.ifPresent(diagram -> this.diagram = diagram);
        }
    }
}

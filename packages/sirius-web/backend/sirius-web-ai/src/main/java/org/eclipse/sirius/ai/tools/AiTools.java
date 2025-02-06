package org.eclipse.sirius.ai.tools;

import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.api.IDiagramEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.diagrams.Diagram;
import org.springframework.context.annotation.Lazy;

import java.util.Objects;
import java.util.Optional;

public abstract class AiTools {

    private final IEditingContextSearchService editingContextSearchService;

    private final IRepresentationSearchService representationSearchService;

    final IDiagramEventHandler diagramEventHandler;

    final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    IInput input = null;

    IEditingContext editingContext = null;

    Diagram diagram = null;

    public AiTools(IRepresentationSearchService representationSearchService,
                   IEditingContextSearchService editingContextSearchService,
                   @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                   IDiagramEventHandler diagramEventHandler) {
        this.editingContextSearchService = editingContextSearchService;
        this.representationSearchService = Objects.requireNonNull(representationSearchService);
        this.diagramEventHandler = Objects.requireNonNull(diagramEventHandler);
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                           PRIVATE GETTERS AND METHODS
    // ---------------------------------------------------------------------------------------------------------------

    public void setInput(IInput input) {
        this.input = input;
    }

    protected IInput getInput() {
        return input;
    }

    protected IEditingContext getEditingContext(IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            Optional<IEditingContext> optionalEditingContext = this.editingContextSearchService.findById(aiRequestInput.editingContextId());
            return optionalEditingContext.orElse(null);
        }
        throw new IllegalArgumentException("Input is not an AiRequestInput");
    }

    protected Diagram getDiagram(IInput input) throws Exception {
        if (input instanceof AiRequestInput aiRequestInput) {
            if (this.editingContext == null) {
                this.editingContext = this.getEditingContext(aiRequestInput);
            }

            var optionalDiagram = this.representationSearchService.findById(this.editingContext, aiRequestInput.representationId(), Diagram.class);
            if (optionalDiagram.isPresent()) {
                return optionalDiagram.get();
            }
        }
        throw new IllegalArgumentException("Input is not an AiRequestInput");
    }
}

package org.eclipse.sirius.ai.tool.edition;

import org.eclipse.sirius.ai.tool.AiTools;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetConnectorToolsEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class LinkEditionTools extends AiTools {
    public LinkEditionTools(IRepresentationSearchService representationSearchService,
                            IEditingContextSearchService editingContextSearchService,
                            @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                            GetConnectorToolsEventHandler getConnectorToolsEventHandler) {
        super(representationSearchService, editingContextSearchService, editingContextEventProcessorRegistry, getConnectorToolsEventHandler);
    }


}

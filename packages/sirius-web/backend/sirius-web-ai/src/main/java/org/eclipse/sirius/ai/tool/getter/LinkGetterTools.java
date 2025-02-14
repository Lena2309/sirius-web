package org.eclipse.sirius.ai.tool.getter;

import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.tool.AiTools;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetConnectorToolsEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LinkGetterTools extends AiTools {
    public LinkGetterTools(IRepresentationSearchService representationSearchService,
                           IEditingContextSearchService editingContextSearchService,
                           @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                           GetConnectorToolsEventHandler getConnectorToolsEventHandler) {
        super(representationSearchService, editingContextSearchService, editingContextEventProcessorRegistry, getConnectorToolsEventHandler);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                               EXISTING LINKS GETTER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a Map of existing Links IDs structured as: {link id, { source id, target id }}")
    public Map<String, Map<String, String>> getExistingDiagramLinksIds() {
        var availableLinks = new HashMap<String, Map<String, String>>();
        var sourceAndTargetNodes = new HashMap<String, String>();

        this.refreshDiagram();

        for (var edge : this.diagram.getEdges()) {
            sourceAndTargetNodes.put(UUIDConverter.compress(edge.getSourceId()), UUIDConverter.compress(edge.getTargetId()));
            availableLinks.put(UUIDConverter.compress(edge.getId()), sourceAndTargetNodes);
        }

        return availableLinks;
    }
}

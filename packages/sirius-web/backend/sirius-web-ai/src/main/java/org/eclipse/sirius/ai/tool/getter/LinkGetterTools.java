package org.eclipse.sirius.ai.tool.getter;

import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.tool.AiTool;
import org.eclipse.sirius.ai.service.AiToolService;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.core.api.IInput;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LinkGetterTools implements AiTool {
    private final AiToolService aiToolService;

    public LinkGetterTools(AiToolService aiToolService) {
        this.aiToolService = aiToolService;
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                               EXISTING LINKS GETTER
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a Map of existing Links IDs structured as: {link id, { source id, target id }}")
    public Map<String, Map<String, String>> getExistingDiagramLinksIds() {
        var availableLinks = new HashMap<String, Map<String, String>>();
        var sourceAndTargetNodes = new HashMap<String, String>();

        this.aiToolService.refreshDiagram();

        for (var edge : this.aiToolService.getDiagram().getEdges()) {
            sourceAndTargetNodes.put(UUIDConverter.compress(edge.getSourceId()), UUIDConverter.compress(edge.getTargetId()));
            availableLinks.put(UUIDConverter.compress(edge.getId()), sourceAndTargetNodes);
        }

        return availableLinks;
    }
}

package org.eclipse.sirius.web.ai.tool.getter;

import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
import org.eclipse.sirius.components.core.api.IInput;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class LinkGetterTools implements AiTool {
    private final AiToolService aiToolService;

    public LinkGetterTools(AiToolService aiToolService) {
        this.aiToolService = Objects.requireNonNull(aiToolService);
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
    }

    /*

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
    */
}

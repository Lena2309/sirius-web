package org.eclipse.sirius.ai.tool.edition;

import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.service.AiToolService;
import org.eclipse.sirius.ai.tool.AiTool;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IInput;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LinkEditionTools implements AiTool {
    private final EditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final AiToolService aiToolService;

    private final EditionToolService editionToolService;

    public LinkEditionTools(@Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                              AiToolService aiToolService, EditionToolService editionToolService) {
        this.editingContextEventProcessorRegistry = editingContextEventProcessorRegistry;
        this.aiToolService = aiToolService;
        this.editionToolService = editionToolService;
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
        this.editionToolService.setAiToolService(this.aiToolService);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  GET LINK PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a Map of an existing link properties structured as {property label, [property value options]} OR {property label, property current value}.")
    public Map<String, Map<String, Object>> getLinkProperties(String linkId) {
        var form = this.editionToolService.getFormForObject(linkId, false);

        return this.editionToolService.getProperties(form);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT LINK PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Edit the value of an existing link property.")
    public String editLinkSingleValueProperty(String linkId, String propertyLabel, String newPropertyValue) {
        this.aiToolService.refreshDiagram();

        var objectNode = this.aiToolService.findNode(UUIDConverter.decompress(linkId).toString());
        assert objectNode != null;
        var representationId = new StringBuilder("details://?objectIds=[").append(objectNode.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(linkId, propertyLabel, false);

        return this.editionToolService.changePropertySingleValue(newPropertyValue, widget, representationId, this.editingContextEventProcessorRegistry);
    }

    @Tool("Edit the values of an existing link property that can contain multiple ones at once.")
    public String editLinkMultipleValueProperty(String linkId, String propertyLabel, List<String> newPropertyValues) {
        var objectNode = this.aiToolService.findNode(UUIDConverter.decompress(linkId).toString());
        assert objectNode != null;
        var representationId = new StringBuilder("details://?objectIds=[").append(objectNode.getTargetObjectId()).append("]");

        var widget = this.editionToolService.getWidget(linkId, propertyLabel, false);

        return this.editionToolService.changePropertyMultipleValue(newPropertyValues, widget, representationId, this.editingContextEventProcessorRegistry);
    }
}

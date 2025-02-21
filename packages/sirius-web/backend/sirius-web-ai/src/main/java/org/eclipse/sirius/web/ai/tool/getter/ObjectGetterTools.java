package org.eclipse.sirius.web.ai.tool.getter;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.web.ai.util.PairDiagramElement;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.diagrams.Node;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ObjectGetterTools implements AiTool {
    private final AiToolService aiToolService;

    public ObjectGetterTools(AiToolService aiToolService) {
        this.aiToolService = aiToolService;
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                               EXISTING OBJECTS GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a List of existing root object IDs structured as: {object type, object id}. The ids should not be modified.")
    public List<PairDiagramElement> getExistingObjectsIds() {
        var availableObjects = new ArrayList<PairDiagramElement>();

        this.aiToolService.refreshDiagram();

        for (var node : this.aiToolService.getDiagram().getNodes()) {
            availableObjects.add(new PairDiagramElement(node.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=",""), UUIDConverter.compress(node.getId())));
        }

        return availableObjects;
    }

    @Tool("Retrieve a List of existing children IDs structured as: {child type, child id}. Useless for freshly created objects, or when creating objects at root. The ids should not be modified.")
    public List<PairDiagramElement> getExistingChildrenIdsFromSpecificParent(@P("The parent object. Not the diagram root.") String parentObjectId) {
        var availableChildNodes = new ArrayList<PairDiagramElement>();
        this.aiToolService.refreshDiagram();

        var parentNode = this.aiToolService.findNode(UUIDConverter.decompress(parentObjectId).toString());

        assert parentNode != null;
        for (var child : parentNode.getChildNodes()) {
            availableChildNodes.add(new PairDiagramElement(child.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=",""), UUIDConverter.compress(child.getId())));
        }

        return availableChildNodes;
    }

    // TODO: doesn't scale
    public Map<PairDiagramElement, List<PairDiagramElement>> getExistingObjectsAndChildren() {
        var existingParentsAndChildren = new HashMap<PairDiagramElement, List<PairDiagramElement>>();
        var existingParents = getExistingObjectsIds();

        for (var parent : existingParents) {
            var children = this.getExistingChildrenIdsFromSpecificParent(parent.elementId());
            existingParentsAndChildren.put(parent, children);
        }

        return existingParentsAndChildren;
    }

    @Tool("Retrieve a Map of all existing children IDs structured as: {parentId, [{child type, child id}]}. The ids should not be modified.")
    public Map<String, List<PairDiagramElement>> getAllExistingChildrenIds() {
        var childrenIds = new HashMap<String, List<PairDiagramElement>>();

        this.aiToolService.refreshDiagram();

        for (Node parent : this.aiToolService.getDiagram().getNodes()) {
            var availableChildNodes = new ArrayList<PairDiagramElement>();

            for (var child : parent.getChildNodes()) {
                availableChildNodes.add(new PairDiagramElement(child.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=", ""), UUIDConverter.compress(child.getId())));
            }
            childrenIds.put(UUIDConverter.compress(parent.getId()), availableChildNodes);
        }

        return childrenIds;
    }
}

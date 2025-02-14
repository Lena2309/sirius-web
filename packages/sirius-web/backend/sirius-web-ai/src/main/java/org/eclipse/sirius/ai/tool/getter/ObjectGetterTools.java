package org.eclipse.sirius.ai.tool.getter;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.tool.AiTools;
import org.eclipse.sirius.ai.util.PairDiagramElement;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetPaletteInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.GetPaletteSuccessPayload;
import org.eclipse.sirius.components.collaborative.diagrams.dto.InvokeSingleClickOnDiagramElementToolInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.ToolSection;
import org.eclipse.sirius.components.collaborative.diagrams.handlers.GetPaletteEventHandler;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.diagrams.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ObjectGetterTools extends AiTools {
    public ObjectGetterTools(IRepresentationSearchService representationSearchService,
                             IEditingContextSearchService editingContextSearchService,
                             @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                             GetPaletteEventHandler paletteEventHandler) {
        super(representationSearchService, editingContextSearchService, editingContextEventProcessorRegistry, paletteEventHandler);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                               EXISTING OBJECTS GETTERS
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a List of existing root object IDs structured as: {object type, object id}. The ids should not be modified.")
    public List<PairDiagramElement> getExistingObjectsIds() {
        var availableObjects = new ArrayList<PairDiagramElement>();

        this.refreshDiagram();

        for (var node : this.diagram.getNodes()) {
            availableObjects.add(new PairDiagramElement(node.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=",""), UUIDConverter.compress(node.getId())));
        }

        return availableObjects;
    }

    @Tool("Retrieve a List of existing children IDs structured as: {child type, child id}. Useless for freshly created objects, or when creating objects at root. The ids should not be modified.")
    public List<PairDiagramElement> getExistingChildrenIdsFromSpecificParent(@P("The parent object. Not the diagram root.") String parentObjectId) {
        var availableChildNodes = new ArrayList<PairDiagramElement>();
        this.refreshDiagram();

        var parentNode = this.diagram.getNodes().stream()
                .filter(node -> Objects.equals(node.getId(), UUIDConverter.decompress(parentObjectId).toString()))
                .findFirst();

        assert parentNode.isPresent();
        for (var child : parentNode.get().getChildNodes()) {
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

        this.refreshDiagram();

        for (Node parent : this.diagram.getNodes()) {
            var availableChildNodes = new ArrayList<PairDiagramElement>();

            for (var child : parent.getChildNodes()) {
                availableChildNodes.add(new PairDiagramElement(child.getTargetObjectKind().replace("siriusComponents://semantic?domain=flow&entity=", ""), UUIDConverter.compress(child.getId())));
            }
            childrenIds.put(UUIDConverter.compress(parent.getId()), availableChildNodes);
        }

        return childrenIds;
    }
}

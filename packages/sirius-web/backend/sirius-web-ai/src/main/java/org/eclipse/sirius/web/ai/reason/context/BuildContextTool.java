package org.eclipse.sirius.web.ai.reason.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.sirius.components.core.api.IRepresentationDescriptionSearchService;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.description.NodeDescription;
import org.eclipse.sirius.components.diagrams.tools.Palette;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.web.ai.util.*;
import org.eclipse.sirius.web.application.editingcontext.EditingContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;

@Service
public class BuildContextTool implements AiTool {
    private final IRepresentationDescriptionSearchService representationDescriptionSearchService;

    private final AiToolService aiToolService;

    public BuildContextTool(IRepresentationDescriptionSearchService representationDescriptionSearchService,
                            AiToolService aiToolService) {
        this.representationDescriptionSearchService = Objects.requireNonNull(representationDescriptionSearchService);
        this.aiToolService = Objects.requireNonNull(aiToolService);
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
    }

    protected record DiagramObject(String objectType, String description, List<String> supertypes) {}

    // ---------------------------------------------------------------------------------------------------------------
    //                                                DIAGRAM DESCRIPTION GETTER
    // ---------------------------------------------------------------------------------------------------------------

    private Optional<DiagramDescription> getDiagramDescription() {
        this.aiToolService.refreshDiagram();
        return this.representationDescriptionSearchService.findById(this.aiToolService.getEditingContext(), aiToolService.getDiagram().getDescriptionId())
                .filter(DiagramDescription.class::isInstance)
                .map(DiagramDescription.class::cast);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                BUILD DOMAIN CONTEXT
    // ---------------------------------------------------------------------------------------------------------------

    public String buildDomainContext() {
        var diagramDescription = getDiagramDescription();
        var mapper = new ObjectMapper();

        assert diagramDescription.isPresent();
        var jsonContext = new ContextJsonFormat(
                buildObjectContext(diagramDescription.get()),
                buildLinkContext(diagramDescription.get())
        );

        try {
            return mapper.writeValueAsString(jsonContext);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing to JSON", e);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                BUILD OBJECT CONTEXT
    // ---------------------------------------------------------------------------------------------------------------

    private List<JsonObject> buildObjectContext(DiagramDescription diagramDescription) {
        var jsonRootObjects = new ArrayList<JsonObject>();

        var childrenMapping = new HashMap<String, List<String>>();
        var objectTypeToNodeDescription = new HashMap<String, NodeDescription>();
        var idToObjectType = new HashMap<String, String>();
        var rootObjects = new ArrayList<String>();
        var childObjects = new ArrayList<String>();

        // Retrieve the nodes descriptions from the diagram description
        diagramDescription.getNodeDescriptions().forEach(node -> extractObjectTypes(node, idToObjectType, objectTypeToNodeDescription));

        // Retrieve the possible objects that can be build
        diagramDescription.getPalettes().forEach(palette -> {
            if (palette.getId().contains("diagramPalette")) {
                extractPaletteObjects(palette, rootObjects);
            } else if (palette.getId().contains("nodePalette")) {
                extractPaletteObjects(palette, childObjects);
            }
        });

        // Retrieve the EClasses of the domain
        var packageRegistry = ((EditingContext) aiToolService.getEditingContext()).getDomain().getResourceSet().getPackageRegistry();
        var firstDomain = packageRegistry.values().stream().findFirst();
        var diagramObjectsList = firstDomain.filter(EPackage.class::isInstance)
                .map(EPackage.class::cast)
                .map(ep -> ep.getEClassifiers().stream()
                        .filter(EClass.class::isInstance)
                        .map(EClass.class::cast)
                        .filter(Predicate.not(EClass::isAbstract))
                        .filter(ec -> !ec.getESuperTypes().isEmpty())
                        .map(ec -> new DiagramObject(ec.getName(), ec.getEAnnotations().toString(), ec.getESuperTypes().stream().map(EClass::getName).toList()))
                        .toList())
                .orElse(List.of());

        rootObjects.forEach(object ->
            jsonRootObjects.add(new JsonObject(object, getDescription(object, diagramObjectsList), writeRecursiveChildren(object, childrenMapping, objectTypeToNodeDescription, idToObjectType, diagramObjectsList, 0)))
        );

        return jsonRootObjects;
    }

    private static List<JsonObject> writeRecursiveChildren(String parent, Map<String, List<String>> childrenMapping, Map<String, NodeDescription> objectTypeToNodeDescription, Map<String, String> idToObjectType, List<DiagramObject> diagramObjectsList, int depth) {
        var children = new ArrayList<JsonObject>();

        // TODO: vraiment pas idéal et peut être source d'hallucinations plus tard
        for (var objectType : objectTypeToNodeDescription.keySet()) {
            if (objectType.contains(parent)) {
                if (childrenMapping.containsKey(objectType)) {
                    for (var child : childrenMapping.get(objectType)) {
                        if (childrenMapping.containsKey(child)) {
                            children.add(new JsonObject(child, getDescription(child, diagramObjectsList), writeRecursiveChildren(child, childrenMapping, objectTypeToNodeDescription, idToObjectType, diagramObjectsList, depth + 1)));
                        } else {
                            children.add(new JsonObject(child, getDescription(child, diagramObjectsList), retrieveRecursiveChildren(child, childrenMapping, objectTypeToNodeDescription, idToObjectType, diagramObjectsList, depth + 1)));
                        }
                    }
                } else {
                    children.addAll(retrieveRecursiveChildren(objectType, childrenMapping, objectTypeToNodeDescription, idToObjectType, diagramObjectsList, depth));
                }
                break;
            }
        }

        if (depth < 5) {
            // inherit children of supertypes
            for (var diagramObject : diagramObjectsList) {
                if (diagramObject.objectType.equals(parent)) {
                    for (var supertype : diagramObject.supertypes) {
                        children.addAll(writeRecursiveChildren(supertype, childrenMapping, objectTypeToNodeDescription, idToObjectType, diagramObjectsList, depth + 1));
                    }
                    break;
                }
            }
        }

        return children;
    }

    private static String getDescription(String objectType, List<DiagramObject> diagramObjectsList) {
        String description = "";
        for (var diagramObject : diagramObjectsList) {
            if (diagramObject.objectType.equals(objectType)) {
                description = diagramObject.description;
            }
        }
        return description;
    }

    private static List<JsonObject> retrieveRecursiveChildren(String objectType, Map<String, List<String>> childrenMapping, Map<String, NodeDescription> objectTypeToNodeDescription, Map<String, String> idToObjectType, List<DiagramObject> diagramObjectsList, int depth) {
        var children = new ArrayList<JsonObject>();

        if (objectTypeToNodeDescription.containsKey(objectType)) {
            var reusedChildren = objectTypeToNodeDescription.get(objectType).getReusedChildNodeDescriptionIds();

            if (!reusedChildren.isEmpty()) {
                var childrenTypes = new ArrayList<String>();
                for (var childId : reusedChildren) {
                    var childType = idToObjectType.get(childId);
                    if ((childType != null) && (depth < 5)) {
                        childrenTypes.add(childType);
                        children.add(new JsonObject(childType, getDescription(childType, diagramObjectsList), writeRecursiveChildren(childType, childrenMapping, objectTypeToNodeDescription, idToObjectType, diagramObjectsList, depth + 1)));
                    }
                }
                childrenMapping.put(objectType, childrenTypes);
            }
        }

        return children;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                BUILD LINK CONTEXT
    // ---------------------------------------------------------------------------------------------------------------

    private List<JsonLink> buildLinkContext(DiagramDescription diagramDescription) {
        var links = new ArrayList<JsonLink>(List.of());

        for (var link : diagramDescription.getEdgeDescriptions()) {
            if (link.getCenterLabelDescription() != null) {
                links.add(new JsonLink(extractLinkType(link.getCenterLabelDescription().getId())));
            }
        }

        return links;
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                     EXTRACTORS
    // ---------------------------------------------------------------------------------------------------------------

    private static void extractObjectTypes(NodeDescription node, Map<String, String> idToObjectType, Map<String, NodeDescription> objectTypeToNodeDescription) {
        String objectType;

        if (node.getInsideLabelDescription() != null) {
            objectType = extractObjectType(node.getInsideLabelDescription().getId());
            idToObjectType.put(node.getId(), objectType);
            objectTypeToNodeDescription.put(objectType, node);
        }

        node.getOutsideLabelDescriptions().stream()
                .filter(Objects::nonNull)
                .map(label -> Map.entry(node.getId(), extractObjectType(label.getId())))
                .forEach(entry -> {
                    idToObjectType.put(entry.getKey(), entry.getValue());
                    objectTypeToNodeDescription.put(entry.getValue(), node);
                });
    }

    private static void extractPaletteObjects(Palette palette, ArrayList<String> childObject) {
        var toolSections = palette.getToolSections().stream()
                .filter(toolSection -> !toolSection.getLabel().equals("Show/Hide")).toList();

        for (var toolSection : toolSections) {
            for (var tool : toolSection.getTools()) {
                childObject.add(tool.getLabel().replace(" ", "").replace("New", ""));
            }
        }
    }

    private static String extractObjectType(String id) {
        var result = "";
        var strings = id.split("@");

        for (var str : strings) {
            if (str.contains("nodeDescriptions")) {
                var nodeDescription = str.split("]");
                for (var s : nodeDescription) {
                    if(s.contains("nodeDescriptions")) {
                        result = s.replace("nodeDescriptions[name=", "").replace("%20", "").replace("'", "").replace("Node", "").replace(" ","");
                        break;
                    }
                }
            }
        }

        return result;
    }

    private static String extractLinkType(String id) {
        var result = "";
        var strings = id.split("@");

        for (var str : strings) {
            if (str.contains("edgeDescriptions")) {
                result = str.replace("edgeDescriptions[name='", "").replace("%20", " ").replace("']_centerlabel", "");
                break;
            }
        }

        return result;
    }
}

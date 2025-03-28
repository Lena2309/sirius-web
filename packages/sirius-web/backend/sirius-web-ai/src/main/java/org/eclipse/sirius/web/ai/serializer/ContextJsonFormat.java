package org.eclipse.sirius.web.ai.serializer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ContextJsonFormat {
    @JsonProperty("possible_root_objects")
    private final List<JsonObject> rootObjects;


    @JsonProperty("possible_links")
    private final List<JsonLink> links;

    public ContextJsonFormat(List<JsonObject> rootObjects, List<JsonLink> links) {
        this.rootObjects = rootObjects;
        this.links = links;
    }
}
package org.eclipse.sirius.web.ai.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class JsonObject {
    @JsonProperty("name")
    private final String name;

    @JsonProperty("possible_children")
    private final List<JsonObject> children;

    public JsonObject(String name, List<JsonObject> children) {
        this.name = name;
        this.children = children;
    }
}
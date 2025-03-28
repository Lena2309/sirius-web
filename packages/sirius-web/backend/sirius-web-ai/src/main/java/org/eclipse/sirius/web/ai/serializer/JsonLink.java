package org.eclipse.sirius.web.ai.serializer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonLink {
    @JsonProperty("source_to_target")
    private final String name;

    public JsonLink(String name) {
        this.name = name;
    }
}
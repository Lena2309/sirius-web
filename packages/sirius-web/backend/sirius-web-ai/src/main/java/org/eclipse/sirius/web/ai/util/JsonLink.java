package org.eclipse.sirius.web.ai.util;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonLink {
    @JsonProperty("type")
    private final String name;

    public JsonLink(String name) {
        this.name = name;
    }
}
/***********************************************************************************************
 * Copyright (c) 2025 Obeo. All Rights Reserved.
 * This software and the attached documentation are the exclusive ownership
 * of its authors and was conceded to the profit of Obeo S.A.S.
 * This software and the attached documentation are protected under the rights
 * of intellectual ownership, including the section "Titre II  Droits des auteurs (Articles L121-1 L123-12)"
 * By installing this software, you acknowledge being aware of these rights and
 * accept them, and as a consequence you must:
 * - be in possession of a valid license of use conceded by Obeo only.
 * - agree that you have read, understood, and will comply with the license terms and conditions.
 * - agree not to do anything that could conflict with intellectual ownership owned by Obeo or its beneficiaries
 * or the authors of this software.
 *
 * Should you not agree with these terms, you must stop to use this software and give it back to its legitimate owner.
 ***********************************************************************************************/
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
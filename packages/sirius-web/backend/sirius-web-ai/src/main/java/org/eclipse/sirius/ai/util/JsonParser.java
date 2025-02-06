package org.eclipse.sirius.ai.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class JsonParser {
    public static Map<String, Object> parseJsonToMap(String jsonString) throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, Map.class);
    }
}
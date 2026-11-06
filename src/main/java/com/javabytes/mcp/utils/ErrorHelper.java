package com.javabytes.mcp.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ErrorHelper {
    public static final ObjectMapper MAPPER = JsonUtils.MAPPER;

    public static ObjectNode error(String errorCode, String message) {
        ObjectNode error = MAPPER.createObjectNode();
        error.put("success", false);
        error.put("error", errorCode);
        error.put("message", message);
        return error;
    }

    public static ObjectNode success() {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("success", true);
        return result;
    }
}

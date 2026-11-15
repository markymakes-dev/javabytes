package com.javabytes.mcp.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.Outcome;

/**
 * Error code vocabulary and factory methods — now a thin delegate over
 * {@link Outcome}. The SCREAMING_CASE codes stay exactly as they were;
 * tools continue to use these names.
 *
 * <p>Legacy methods return {@link JsonNode} directly for backward compatibility
 * with the 70+ existing tools. New code should use {@link #outcomeError(String, String)}
 * and {@link #outcomeSuccess(Object)} with {@link Outcome#toWire()}.</p>
 */
public final class ErrorHelper {

    public static final ObjectMapper MAPPER = JsonUtils.MAPPER;

    // --- error codes (SCREAMING_CASE — tool vocabulary) ---------------------

    public static final String MISSING_PARAM = "MISSING_PARAM";
    public static final String INVALID_IDENTIFIER = "INVALID_IDENTIFIER";
    public static final String PATH_NOT_ALLOWED = "PATH_NOT_ALLOWED";
    public static final String PATH_NOT_FOUND = "PATH_NOT_FOUND";
    public static final String DB_ERROR = "DB_ERROR";
    public static final String GENERATE_DATABASE_ERROR = "GENERATE_DATABASE_ERROR";
    public static final String NO_DATABASE = "NO_DATABASE";
    public static final String TABLE_ALREADY_EXISTS = "TABLE_ALREADY_EXISTS";
    public static final String INVALID_FUNCTIONAL_DEPENDENCIES = "INVALID_FUNCTIONAL_DEPENDENCIES";
    public static final String DDL_EXECUTION_ERROR = "DDL_EXECUTION_ERROR";

    private ErrorHelper() {
    }

    // --- legacy: return JsonNode directly (backward compat for 70+ tools) ---

    /**
     * Create an error node in the wire format {success:false, error, message}.
     * This is the method all existing tools call.
     */
    public static ObjectNode error(String errorCode, String message) {
        ObjectNode error = MAPPER.createObjectNode();
        error.put("success", false);
        error.put("error", errorCode);
        error.put("message", message);
        return error;
    }

    /**
     * Create a success node {success:true}.
     */
    public static ObjectNode success() {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("success", true);
        return result;
    }

    // --- new: return typed Outcome (for incremental migration) -------------

    /**
     * Create a failure outcome with the given error code and message.
     */
    public static <T> Outcome<T> outcomeError(String errorCode, String message) {
        return Outcome.failure(errorCode, message);
    }

    /**
     * Create a success outcome with the given data.
     */
    public static <T> Outcome<T> outcomeSuccess(T data) {
        return Outcome.success(data);
    }

    /**
     * Create a success outcome with no data.
     */
    public static Outcome<Void> outcomeSuccess() {
        return Outcome.success(null);
    }
}
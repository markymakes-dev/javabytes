package com.javabytes.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.utils.JsonUtils;

/**
 * A tool's typed result — either success with data, or failure with a
 * standardized error code and message. The JSON-RPC envelope is built
 * from this in exactly one place (the transport layer).
 *
 * <p>Error codes are the SCREAMING_CASE vocabulary from {@link ErrorHelper}
 * plus the two reserved wire codes {@code SKILL_NOT_FOUND} and
 * {@code SKILL_EXECUTION_ERROR} which only the registry produces.</p>
 */
public final class Outcome<T> {

    private final boolean success;
    private final T data;
    private final String errorCode;
    private final String message;

    private Outcome(boolean success, T data, String errorCode, String message) {
        this.success = success;
        this.data = data;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static <T> Outcome<T> success(T data) {
        return new Outcome<>(true, data, null, null);
    }

    public static <T> Outcome<T> failure(String errorCode, String message) {
        return new Outcome<>(false, null, errorCode, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public T getData() {
        if (!success) {
            throw new IllegalStateException("Cannot get data from a failure outcome");
        }
        return data;
    }

    public String getErrorCode() {
        if (success) {
            throw new IllegalStateException("Cannot get error code from a success outcome");
        }
        return errorCode;
    }

    public String getMessage() {
        if (success) {
            throw new IllegalStateException("Cannot get message from a success outcome");
        }
        return message;
    }

    /**
     * Serialize to the JSON-RPC envelope: {success, error?, message?}.
     * This is the ONLY place that constructs the wire envelope.
     */
    public JsonNode toWire() {
        ObjectNode wire = JsonUtils.MAPPER.createObjectNode();
        wire.put("success", success);
        if (!success) {
            wire.put("error", errorCode);
            wire.put("message", message);
        } else if (data instanceof JsonNode node) {
            // For JsonNode data, merge its fields into the envelope
            // (backward compat: tools already put everything under success)
            if (node.isObject()) {
                node.fields().forEachRemaining(entry -> wire.set(entry.getKey(), entry.getValue()));
            }
        }
        return wire;
    }

    /**
     * Serialize a raw JSON node directly (for backward compat with tools
     * that still return JsonNode directly).
     */
    public static JsonNode toWire(JsonNode raw) {
        // Assume it's already the wire format
        return raw;
    }
}
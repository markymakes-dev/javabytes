package com.javabytes.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;

public interface Tool {

    String getName();

    /** This tool's self-declared catalog entry (ADR 0006). */
    ToolSpec spec();

    JsonNode execute(JsonNode params);

    JsonSchema getInputSchema();

    static class JsonSchema {
        private java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();
        private java.util.List<String> required = new java.util.ArrayList<>();
        private String description;

        public JsonSchema prop(String name, Object schema) {
            properties.put(name, schema);
            return this;
        }

        /** Alias for prop() so generated tools can use either name */
        public JsonSchema property(String name, Object schema) {
            return prop(name, schema);
        }

        public JsonSchema required(String name) {
            required.add(name);
            return this;
        }

        public JsonSchema description(String desc) {
            this.description = desc;
            return this;
        }

        public java.util.Map<String, Object> getProperties() { return properties; }
        public java.util.List<String> getRequired() { return required; }
        public String getDescription() { return description; }
    }

    static class StringSchema {
        private String description;
        public StringSchema description(String desc) { this.description = desc; return this; }
        public String getDescription() { return description; }
    }

    static class BoolSchema {
        private String description;
        public BoolSchema description(String desc) { this.description = desc; return this; }
        public String getDescription() { return description; }
    }

    static class NumberSchema {
        private String description;
        private Double minimum;
        private Double maximum;
        private boolean integer;

        public NumberSchema description(String desc) { this.description = desc; return this; }
        public NumberSchema minimum(double min) { this.minimum = min; return this; }
        public NumberSchema maximum(double max) { this.maximum = max; return this; }
        public NumberSchema integer() { this.integer = true; return this; }
        public String getDescription() { return description; }
        public Double getMinimum() { return minimum; }
        public Double getMaximum() { return maximum; }
        public boolean isInteger() { return integer; }
    }
}

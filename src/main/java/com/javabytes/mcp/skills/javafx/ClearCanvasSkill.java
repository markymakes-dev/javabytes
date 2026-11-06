package com.javabytes.mcp.skills.javafx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import javafx.scene.canvas.GraphicsContext;

public class ClearCanvasSkill implements Skill {

    @Override
    public String getName() { return "javafx_clear_canvas"; }

    @Override
    public JsonNode execute(JsonNode params) {
        try {
            GraphicsContext gc = CanvasManager.getGc();
            if (gc == null) {
                return ErrorHelper.error("CANVAS_NOT_INIT", "Canvas not initialized");
            }
            gc.clearRect(0, 0, CanvasManager.getCanvas().getWidth(), CanvasManager.getCanvas().getHeight());
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("message", "Canvas cleared");
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("CLEAR_ERROR", "Error clearing canvas: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        return new Skill.JsonSchema();
    }
}

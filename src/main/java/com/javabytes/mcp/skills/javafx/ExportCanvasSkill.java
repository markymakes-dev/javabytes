package com.javabytes.mcp.skills.javafx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Renders the active canvas to a PNG using a JavaFX snapshot.
 *
 * <p>The snapshot runs on the JavaFX application thread; the toolkit is started
 * in headless (Monocle) mode when it is not running yet.</p>
 */
public class ExportCanvasSkill implements Skill {

    @Override
    public String getName() { return "javafx_export_image"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String path = params.has("path") ? params.get("path").asText() : "canvas.png";
        try {
            Path output = Path.of(path);
            if (!PathValidator.isAllowed(output)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + path + ". Allowed roots: "
                                + AppConfig.describeAllowedDirectories());
            }
            Canvas canvas = CanvasManager.getCanvas();
            if (canvas == null) {
                return ErrorHelper.error("CANVAS_NOT_INIT", "Canvas not initialized. Call javafx_init_canvas first.");
            }

            WritableImage image = snapshot(canvas);
            BufferedImage buffered = SwingFXUtils.fromFXImage(image, null);
            if (buffered == null) {
                return ErrorHelper.error("EXPORT_ERROR", "JavaFX snapshot could not be converted to an image");
            }

            Path absolute = output.toAbsolutePath();
            if (absolute.getParent() != null) {
                Files.createDirectories(absolute.getParent());
            }
            boolean written = ImageIO.write(buffered, "png", absolute.toFile());
            if (!written) {
                return ErrorHelper.error("EXPORT_ERROR", "No PNG writer is available");
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("format", "png");
            result.put("path", absolute.toString());
            result.put("width", buffered.getWidth());
            result.put("height", buffered.getHeight());
            result.put("size_bytes", Files.size(absolute));
            result.put("message", "Canvas exported to " + absolute);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("EXPORT_ERROR", "Error exporting canvas: " + e.getMessage());
        }
    }

    private WritableImage snapshot(Canvas canvas) throws Exception {
        ensureToolkitStarted();
        try {
            FutureTask<WritableImage> task = new FutureTask<>(() -> canvas.snapshot(null, null));
            Platform.runLater(task);
            return task.get(30, TimeUnit.SECONDS);
        } catch (IllegalStateException e) {
            // Toolkit unavailable: fall back to a direct snapshot.
            return canvas.snapshot(null, null);
        }
    }

    private static volatile boolean toolkitStarted;

    private static synchronized void ensureToolkitStarted() {
        if (toolkitStarted) {
            return;
        }
        try {
            Platform.startup(() -> { });
            toolkitStarted = true;
        } catch (IllegalStateException alreadyStarted) {
            toolkitStarted = true;
        } catch (UnsupportedOperationException | NoClassDefFoundError headlessFailure) {
            // Leave the toolkit flag false so snapshot() uses the direct fallback.
            toolkitStarted = false;
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("path", new Skill.StringSchema().description("Output PNG path inside an allowed directory"));
        schema.required("path");
        return schema;
    }
}

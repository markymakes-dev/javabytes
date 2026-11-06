package com.javabytes.mcp.skills.javafx;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class CanvasManager {
    private static Canvas canvas;
    private static GraphicsContext gc;
    private static Color currentColor = Color.BLACK;

    public static void init(double w, double h) {
        canvas = new Canvas(w, h);
        gc = canvas.getGraphicsContext2D();
        gc.setFill(currentColor);
        gc.setStroke(currentColor);
        gc.setLineWidth(2);
    }
    public static Canvas getCanvas() { return canvas; }
    public static GraphicsContext getGc() { return gc; }
    public static Color getColor() { return currentColor; }
    public static void setColor(Color c) { currentColor = c; }
}

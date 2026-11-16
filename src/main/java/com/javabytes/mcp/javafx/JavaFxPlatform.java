package com.javabytes.mcp.javafx;

/**
 * JavaFX platform initialization for headless environments.
 * Uses Monocle (pure Java) instead of native platform.
 */
public class JavaFxPlatform {

    private static boolean initialized = false;

    /**
     * Initialize JavaFX platform for headless operation.
     * Sets {@code javafx.platform=monocle} system property.
     * Idempotent — safe to call multiple times.
     */
    public static synchronized void init() {
        if (!initialized) {
            System.setProperty("javafx.platform", "monocle");
            initialized = true;
        }
    }

    /**
     * Shutdown JavaFX platform.
     * Currently a no-op (Monocle uses daemon threads).
     * Provided for symmetry and future extension.
     */
    public static synchronized void shutdown() {
        // Monocle uses daemon threads; JVM exits naturally when stdin closes.
        // No explicit shutdown needed.
    }

    /**
     * Check if platform has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
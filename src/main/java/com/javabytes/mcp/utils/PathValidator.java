package com.javabytes.mcp.utils;

import com.javabytes.mcp.config.AppConfig;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class PathValidator {

    public static boolean isAllowed(Path path) {
        return AppConfig.isPathAllowed(path);
    }

    public static boolean isAllowed(String pathString) {
        if (pathString == null) {
            return false;
        }
        try {
            return isAllowed(Path.of(pathString));
        } catch (InvalidPathException e) {
            return false;
        }
    }
}

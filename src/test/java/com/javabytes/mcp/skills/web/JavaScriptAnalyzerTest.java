package com.javabytes.mcp.skills.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaScriptAnalyzerTest {

    @Test
    void detectsFrameworksBrowserApisAndRisks() {
        String source = """
                import React from 'react';
                import axios from 'axios';

                export function App() {
                  return <div onClick={() => fetch('/api')}>{document.title}</div>;
                }
                """;

        ObjectNode analysis = JavaScriptAnalyzer.enrichSource(
                JavaScriptAnalyzer.analyzeSource(source, "App.jsx"));

        assertTrue(analysis.path("frameworks").toString().contains("React"));
        assertTrue(analysis.path("browser_apis").toString().contains("document"));
        assertTrue(analysis.path("browser_apis").toString().contains("fetch"));
        assertTrue(analysis.path("unsupported").toString().contains("JSX"));
        assertEquals("frontend", analysis.path("project_type").asText());
        assertEquals("spring-boot-vaadin", analysis.path("recommended_target").asText());
        assertTrue(analysis.path("dependency_mapping").toString().contains("java.net.http.HttpClient"));
    }

    @Test
    void analyzesAProjectAndSkipsNodeModules(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("package.json"), """
                {
                  "dependencies": {
                    "express": "^4.19.0",
                    "mysql2": "^3.11.0"
                  },
                  "devDependencies": {
                    "jest": "^29.7.0"
                  }
                }
                """);
        Files.writeString(root.resolve("server.js"), """
                const express = require('express');
                const app = express();
                app.get('/users/:id', (req, res) => res.json({}));
                app.post('/users', (req, res) => res.json({}));
                """);
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/App.jsx"), """
                import React from 'react';
                export function App() { return <main>Hello</main>; }
                """);
        Files.createDirectories(root.resolve("node_modules/ignored"));
        Files.writeString(root.resolve("node_modules/ignored/bad.js"), "import 'should-not-appear';");

        ObjectNode analysis = JavaScriptAnalyzer.analyzeProject(root, 100);

        assertEquals(2, analysis.path("files_analyzed").asInt(), analysis.toString());
        assertEquals("fullstack", analysis.path("project_type").asText());
        assertEquals(2, analysis.path("routes").size());
        assertTrue(analysis.path("routes").toString().contains("/users/:id"));
        assertTrue(analysis.path("packages").toString().contains("express"));
        assertTrue(analysis.path("packages").toString().contains("jest"));
        assertFalse(analysis.path("packages").toString().contains("should-not-appear"));
        assertTrue(analysis.path("dependency_mapping").toString().contains("Spring Web MVC"));
        assertTrue(analysis.path("risks").size() >= 2);
    }

    @Test
    void packageNameNormalizationHandlesScopesAndRelativePaths() {
        assertEquals("react", JavaScriptAnalyzer.packageName("react"));
        assertEquals("@angular/core", JavaScriptAnalyzer.packageName("@angular/core/testing"));
        assertEquals("fs", JavaScriptAnalyzer.packageName("node:fs"));
        assertEquals("", JavaScriptAnalyzer.packageName("./local"));
        assertEquals("", JavaScriptAnalyzer.packageName("/absolute"));
    }
}

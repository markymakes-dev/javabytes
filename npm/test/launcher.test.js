"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const { resolveJar, resolveJava, buildArgs, main } = require("../lib/launcher");

function tempDirectory() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "javabytes-npm-"));
}

test("JAVABYTES_JAR takes precedence", () => {
  const directory = tempDirectory();
  const jar = path.join(directory, "custom.jar");
  fs.writeFileSync(jar, "");

  assert.equal(resolveJar({ env: { JAVABYTES_JAR: jar } }), path.resolve(jar));
});

test("falls back to the packaged jar", () => {
  const root = tempDirectory();
  fs.mkdirSync(path.join(root, "jar"));
  const jar = path.join(root, "jar", "javabytes.jar");
  fs.writeFileSync(jar, "");

  assert.equal(resolveJar({ root, env: {} }), jar);
});

test("returns null when no jar exists", () => {
  const root = tempDirectory();
  assert.equal(resolveJar({ root, env: {} }), null);
});

test("JAVABYTES_JAVA wins over JAVA_HOME", () => {
  assert.equal(
    resolveJava({ env: { JAVABYTES_JAVA: "/custom/java", JAVA_HOME: "/ignored" } }),
    "/custom/java"
  );
});

test("JAVA_HOME resolves to bin/java", () => {
  const javaHome = tempDirectory();
  const bin = path.join(javaHome, "bin");
  fs.mkdirSync(bin);
  const executable = process.platform === "win32" ? "java.exe" : "java";
  const java = path.join(bin, executable);
  fs.writeFileSync(java, "");

  assert.equal(resolveJava({ env: { JAVA_HOME: javaHome } }), java);
});

test("builds JVM options before -jar", () => {
  assert.deepEqual(
    buildArgs("server.jar", ["--flag"], { JAVABYTES_JAVA_OPTS: "-Xmx256m -Dfoo=bar" }),
    ["-Xmx256m", "-Dfoo=bar", "-jar", "server.jar", "--flag"]
  );
});

test("main reports a missing jar instead of spawning", async () => {
  const original = console.error;
  console.error = () => {};
  try {
    const code = await main([], {}, tempDirectory());
    assert.equal(code, 1);
  } finally {
    console.error = original;
  }
});

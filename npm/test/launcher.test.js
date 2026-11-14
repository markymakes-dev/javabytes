"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const {
  resolveJar,
  resolveJava,
  buildArgs,
  parseJavaMajor,
  compareVersions,
  looksLikeNpx,
  version,
  doctor,
  main,
} = require("../lib/launcher");

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

// --- version and doctor subcommands ---

/**
 * Swap console.log for a recorder while `run` executes. Every doctor and
 * version test injects javaVersion/fetchLatest stubs, so nothing here
 * spawns java or touches the network.
 */
async function captureStdout(run) {
  const original = console.log;
  const lines = [];
  console.log = (...args) => {
    lines.push(args.join(" "));
  };
  try {
    const result = await run();
    return { result, lines };
  } finally {
    console.log = original;
  }
}

function fixtureRoot({ withJar = false } = {}) {
  const root = tempDirectory();
  fs.writeFileSync(path.join(root, "package.json"), JSON.stringify({ version: "1.0.1" }));
  if (withJar) {
    fs.mkdirSync(path.join(root, "jar"));
    fs.writeFileSync(path.join(root, "jar", "javabytes.jar"), Buffer.alloc(11 * 1024 * 1024));
  }
  return root;
}

function stubJava(overrides = {}) {
  return async () => ({ ok: true, major: 21, version: "21.0.5", ...overrides });
}

function noUpdate() {
  return async () => "1.0.1";
}

test("version prints the package version and jar status", async () => {
  const root = fixtureRoot({ withJar: true });
  const { result, lines } = await captureStdout(() => version({ root }));
  assert.equal(result, 0);
  assert.equal(lines[0], "javabytes-mcp 1.0.1");
  assert.equal(lines[1], "jar: present (11.0 MB)");
});

test("version handles a missing jar gracefully", async () => {
  const root = fixtureRoot();
  const { result, lines } = await captureStdout(() => version({ root }));
  assert.equal(result, 0);
  assert.equal(lines[0], "javabytes-mcp 1.0.1");
  assert.match(lines[1], /^jar: missing - reinstall/);
});

test("doctor passes on a healthy install", async () => {
  const root = fixtureRoot({ withJar: true });
  const { result, lines } = await captureStdout(() =>
    doctor({
      root,
      env: { JAVABYTES_DB_URL: "jdbc:mysql://localhost:3306/appdb" },
      javaVersion: stubJava(),
      fetchLatest: noUpdate(),
    })
  );
  assert.equal(result, 0);
  assert.match(lines[0], /^PASS java: 21\.0\.5 via PATH \(/);
  assert.equal(lines[1], "PASS jar: present (11.0 MB)");
  assert.match(lines[2], /^PASS db: JAVABYTES_DB_URL is set/);
  assert.equal(lines[3], "PASS update: up to date (1.0.1)");
  assert.equal(lines[4], "4 checks, 0 problems");
});

test("doctor warns when java is older than the floor", async () => {
  const root = fixtureRoot({ withJar: true });
  const { result, lines } = await captureStdout(() =>
    doctor({
      root,
      env: {},
      javaVersion: stubJava({ major: 17, version: "17.0.2" }),
      fetchLatest: noUpdate(),
    })
  );
  assert.equal(result, 1);
  assert.match(lines[0], /^WARN java: 17\.0\.2 via PATH/);
  assert.match(lines[0], /Java 21\+/);
  assert.equal(lines.at(-1), "4 checks, 1 problem");
});

test("doctor fails when java cannot be started", async () => {
  const root = fixtureRoot({ withJar: true });
  const { result, lines } = await captureStdout(() =>
    doctor({
      root,
      env: {},
      javaVersion: async () => ({ ok: false, error: new Error("spawn java ENOENT") }),
      fetchLatest: noUpdate(),
    })
  );
  assert.equal(result, 1);
  assert.match(lines[0], /^FAIL java: not found via PATH/);
  assert.match(lines[0], /set JAVA_HOME/);
  assert.equal(lines.at(-1), "4 checks, 1 problem");
});

test("doctor fails when the jar is missing or too small", async () => {
  const missing = fixtureRoot();
  const missingRun = await captureStdout(() =>
    doctor({ root: missing, env: {}, javaVersion: stubJava(), fetchLatest: noUpdate() })
  );
  assert.equal(missingRun.result, 1);
  assert.match(missingRun.lines[1], /^FAIL jar: missing - reinstall/);
  assert.match(missingRun.lines[1], /npm i -g javabytes-mcp@latest/);

  const small = fixtureRoot({ withJar: true });
  fs.writeFileSync(path.join(small, "jar", "javabytes.jar"), "tiny");
  const smallRun = await captureStdout(() =>
    doctor({ root: small, env: {}, javaVersion: stubJava(), fetchLatest: noUpdate() })
  );
  assert.equal(smallRun.result, 1);
  assert.match(smallRun.lines[1], /^FAIL jar: only 0\.0 MB/);
});

test("doctor prints the update command when the registry is newer", async () => {
  const root = fixtureRoot({ withJar: true });
  const { result, lines } = await captureStdout(() =>
    doctor({
      root,
      env: {},
      javaVersion: stubJava(),
      fetchLatest: async () => "1.1.0",
    })
  );
  assert.equal(result, 1);
  const update = lines.find((line) => line.startsWith("WARN update:"));
  assert.match(update, /1\.1\.0 available, 1\.0\.1 installed/);
  assert.match(update, /npm update -g javabytes-mcp/);
  assert.equal(lines.at(-1), "4 checks, 1 problem");
});

test("doctor skips the update check silently when offline", async () => {
  const root = fixtureRoot({ withJar: true });
  const { result, lines } = await captureStdout(() =>
    doctor({
      root,
      env: {},
      javaVersion: stubJava(),
      fetchLatest: async () => {
        throw new Error("getaddrinfo ENOTFOUND");
      },
    })
  );
  assert.equal(result, 0);
  assert.equal(lines.some((line) => line.startsWith("update:")), false);
  assert.match(lines[2], /^INFO db: JAVABYTES_DB_URL is not set/);
  assert.equal(lines.at(-1), "3 checks, 0 problems");
});

test("parses java major versions including the legacy 1.x format", () => {
  assert.equal(parseJavaMajor('openjdk version "21.0.5" 2024-10-15'), 21);
  assert.equal(parseJavaMajor('java version "1.8.0_291"'), 8);
  assert.equal(parseJavaMajor('openjdk version "25" 2025-09-16'), 25);
  assert.equal(parseJavaMajor("no version banner here"), null);
});

test("compares versions numerically", () => {
  assert.equal(compareVersions("1.0.2", "1.0.1"), 1);
  assert.equal(compareVersions("1.0.1", "1.0.1"), 0);
  assert.equal(compareVersions("1.0", "1.0.1"), -1);
  assert.equal(compareVersions("2", "10"), -1);
});

test("detects npx cache installs", () => {
  assert.equal(looksLikeNpx(path.join("cache", "_npx", "abc", "node_modules", "javabytes-mcp")), true);
  assert.equal(looksLikeNpx(path.join("usr", "lib", "node_modules", "javabytes-mcp")), false);
});

test("main routes the version subcommand", async () => {
  const root = fixtureRoot();
  const { result, lines } = await captureStdout(() => main(["version"], {}, root));
  assert.equal(result, 0);
  assert.equal(lines[0], "javabytes-mcp 1.0.1");
});

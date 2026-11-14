"use strict";

const fs = require("node:fs");
const path = require("node:path");

const JAVA_FLOOR = 21;
const MIN_JAR_BYTES = 10 * 1024 * 1024;
const REGISTRY_URL = "https://registry.npmjs.org/javabytes-mcp/latest";
const UPDATE_TIMEOUT_MS = 3000;

function isFile(candidate) {
  try {
    return fs.statSync(candidate).isFile();
  } catch {
    return false;
  }
}

/**
 * Locate the shaded JavaBytes jar.
 *
 * Order: JAVABYTES_JAR, the jar bundled with the npm package, then the
 * development build in target/.
 */
function resolveJar(options = {}) {
  const env = options.env || process.env;
  const root = options.root || path.join(__dirname, "..");

  const candidates = [];
  if (env.JAVABYTES_JAR) {
    candidates.push(path.resolve(env.JAVABYTES_JAR));
  }
  candidates.push(path.join(root, "jar", "javabytes.jar"));
  candidates.push(path.join(root, "..", "target", "javabytes.jar"));

  return candidates.find(isFile) || null;
}

/**
 * Resolve the java executable and report which source won:
 * JAVABYTES_JAVA, JAVA_HOME or PATH. Returns { java, source }.
 */
function resolveJavaWithSource(options = {}) {
  const env = options.env || process.env;
  if (env.JAVABYTES_JAVA) {
    return { java: env.JAVABYTES_JAVA, source: "JAVABYTES_JAVA" };
  }
  if (env.JAVA_HOME) {
    const executable = process.platform === "win32" ? "java.exe" : "java";
    const candidate = path.join(env.JAVA_HOME, "bin", executable);
    if (isFile(candidate)) {
      return { java: candidate, source: "JAVA_HOME" };
    }
  }
  return { java: "java", source: "PATH" };
}

/**
 * Resolve the java executable. JAVABYTES_JAVA wins, then JAVA_HOME, then PATH.
 */
function resolveJava(options = {}) {
  return resolveJavaWithSource(options).java;
}

function buildArgs(jar, argv = [], env = process.env) {
  const options = (env.JAVABYTES_JAVA_OPTS || "").trim();
  const jvmArgs = options ? options.split(/\s+/) : [];
  return [...jvmArgs, "-jar", jar, ...argv];
}

function readPackageJson(root) {
  try {
    return JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8"));
  } catch {
    return { version: "unknown" };
  }
}

function formatMegabytes(bytes) {
  return (bytes / (1024 * 1024)).toFixed(1);
}

function parseJavaVersion(output) {
  const match = /version "([^"]+)"/.exec(output || "");
  return match ? match[1] : null;
}

/**
 * Parse the major java version from `java -version` output. Handles the
 * legacy "1.8.0_291" numbering, returning 8 for it. Unknown output -> null.
 */
function parseJavaMajor(output) {
  const version = parseJavaVersion(output);
  if (!version) {
    return null;
  }
  const parts = version.split(/[._-]/);
  let major = Number.parseInt(parts[0], 10);
  if (major === 1 && parts.length > 1) {
    major = Number.parseInt(parts[1], 10);
  }
  return Number.isNaN(major) ? null : major;
}

/**
 * Run `java -version` (it prints to stderr) and parse the major version.
 * Resolves with { ok, major, version, error } and never rejects, so tests
 * and doctor can treat an unlaunchable java as simply "not ok".
 */
function javaVersion(executable) {
  const { spawn } = require("node:child_process");
  return new Promise((resolve) => {
    const child = spawn(executable, ["-version"], { windowsHide: true });
    let output = "";
    child.stdout.on("data", (chunk) => {
      output += chunk;
    });
    child.stderr.on("data", (chunk) => {
      output += chunk;
    });
    child.on("error", (error) => {
      resolve({ ok: false, error });
    });
    child.on("close", (code) => {
      if (code !== 0) {
        resolve({ ok: false, error: new Error(`'${executable} -version' exited with ${code}`) });
        return;
      }
      resolve({ ok: true, major: parseJavaMajor(output), version: parseJavaVersion(output) });
    });
  });
}

/**
 * Fetch the latest published version from the npm registry. Best-effort and
 * short-timeout: rejects on any network problem so callers can skip silently.
 */
function fetchLatestVersion(url = REGISTRY_URL, timeoutMs = UPDATE_TIMEOUT_MS) {
  const https = require("node:https");
  return new Promise((resolve, reject) => {
    const request = https.get(url, { timeout: timeoutMs }, (response) => {
      if (response.statusCode !== 200) {
        response.resume();
        reject(new Error(`registry responded ${response.statusCode}`));
        return;
      }
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => {
        body += chunk;
      });
      response.on("end", () => {
        try {
          resolve(JSON.parse(body).version);
        } catch (error) {
          reject(error);
        }
      });
    });
    request.on("timeout", () => {
      request.destroy(new Error(`registry did not respond within ${timeoutMs}ms`));
    });
    request.on("error", reject);
  });
}

function compareVersions(left, right) {
  const leftParts = String(left).split(".");
  const rightParts = String(right).split(".");
  const length = Math.max(leftParts.length, rightParts.length);
  for (let index = 0; index < length; index += 1) {
    const leftPart = Number.parseInt(leftParts[index], 10) || 0;
    const rightPart = Number.parseInt(rightParts[index], 10) || 0;
    if (leftPart !== rightPart) {
      return leftPart < rightPart ? -1 : 1;
    }
  }
  return 0;
}

/**
 * npx materializes packages under a cache directory containing an "_npx"
 * segment on every platform; global and local installs never do.
 */
function looksLikeNpx(root) {
  return root.split(path.sep).includes("_npx");
}

/**
 * `javabytes version`: print the package version and jar status. Always
 * exits 0 — the jar line is a status report, not a failure.
 */
function version(options = {}) {
  const env = options.env || process.env;
  const root = options.root || path.join(__dirname, "..");
  const pkg = readPackageJson(root);
  console.log(`javabytes-mcp ${pkg.version}`);

  const jar = resolveJar({ env, root });
  if (jar) {
    console.log(`jar: present (${formatMegabytes(fs.statSync(jar).size)} MB)`);
  } else {
    console.log("jar: missing - reinstall (npm i -g javabytes-mcp@latest)");
  }
  return Promise.resolve(0);
}

/**
 * `javabytes doctor`: check java, the jar, the database config and updates,
 * one PASS/WARN/FAIL (or informational) line each, then a summary line.
 * Resolves with 0 when no problems were found, 1 otherwise. Java resolution
 * and the registry fetch are injectable via options so tests never spawn
 * java or touch the network.
 */
async function doctor(options = {}) {
  const env = options.env || process.env;
  const root = options.root || path.join(__dirname, "..");
  const runJava = options.javaVersion || javaVersion;
  const fetchLatest = options.fetchLatest || fetchLatestVersion;

  let checks = 0;
  const problems = [];
  const report = (status, message) => {
    checks += 1;
    console.log(`${status} ${message}`);
    if (status === "WARN" || status === "FAIL") {
      problems.push(message);
    }
  };

  const { java, source } = resolveJavaWithSource({ env });
  const javaResult = await runJava(java);
  if (!javaResult.ok) {
    report("FAIL", `java: not found via ${source} (${java}) - Install Java 21+ and put it on PATH, or set JAVA_HOME.`);
  } else if (javaResult.major === null || javaResult.major < JAVA_FLOOR) {
    report("WARN", `java: ${javaResult.version || "unknown version"} via ${source} (${java}) - JavaBytes needs Java ${JAVA_FLOOR}+.`);
  } else {
    report("PASS", `java: ${javaResult.version || javaResult.major} via ${source} (${java})`);
  }

  const jar = resolveJar({ env, root });
  if (!jar) {
    report("FAIL", "jar: missing - reinstall (npm i -g javabytes-mcp@latest)");
  } else {
    const bytes = fs.statSync(jar).size;
    if (bytes <= MIN_JAR_BYTES) {
      report("FAIL", `jar: only ${formatMegabytes(bytes)} MB (${jar}) - reinstall (npm i -g javabytes-mcp@latest)`);
    } else {
      report("PASS", `jar: present (${formatMegabytes(bytes)} MB)`);
    }
  }

  if (env.JAVABYTES_DB_URL) {
    report("PASS", `db: JAVABYTES_DB_URL is set (${env.JAVABYTES_DB_URL}); connectivity is not checked here`);
  } else {
    report("INFO", "db: JAVABYTES_DB_URL is not set - MySQL tools will be unconfigured");
  }

  const pkg = readPackageJson(root);
  let latest = null;
  try {
    latest = await fetchLatest(REGISTRY_URL, UPDATE_TIMEOUT_MS);
  } catch {
    latest = null; // offline: skip the update check silently, never fail
  }
  if (latest) {
    if (compareVersions(latest, pkg.version) > 0) {
      const hint = looksLikeNpx(root) ? "npx always runs the latest" : "npm update -g javabytes-mcp";
      report("WARN", `update: ${latest} available, ${pkg.version} installed - ${hint}`);
    } else {
      report("PASS", `update: up to date (${pkg.version})`);
    }
  }

  console.log(`${checks} checks, ${problems.length} ${problems.length === 1 ? "problem" : "problems"}`);
  return problems.length === 0 ? 0 : 1;
}

/**
 * Launcher entry point. With no arguments (the MCP client case) or any
 * unrecognized argument it spawns the server with inherited stdio so the
 * MCP client owns the pipes; "version" and "doctor" are intercepted only
 * as an explicit first argument. Resolves with the process exit code.
 */
function main(argv = process.argv.slice(2), env = process.env, root) {
  const command = argv[0];
  if (command === "version") {
    return version({ env, root });
  }
  if (command === "doctor") {
    return doctor({ env, root });
  }

  const jar = resolveJar({ env, root });
  if (!jar) {
    console.error("[javabytes] Cannot find javabytes.jar.");
    console.error("[javabytes] Install a package that bundles the jar, or set JAVABYTES_JAR");
    console.error("[javabytes] to an existing shaded jar (build one with: mvn package).");
    return Promise.resolve(1);
  }

  const java = resolveJava({ env });
  const { spawn } = require("node:child_process");
  const child = spawn(java, buildArgs(jar, argv, env), {
    stdio: "inherit",
    env,
    windowsHide: true,
  });

  const forward = (signal) => {
    if (child.exitCode === null && child.signalCode === null) {
      child.kill(signal);
    }
  };
  process.on("SIGINT", () => forward("SIGINT"));
  process.on("SIGTERM", () => forward("SIGTERM"));

  return new Promise((resolve) => {
    child.on("error", (error) => {
      console.error(`[javabytes] Failed to start '${java}': ${error.message}`);
      if (error.code === "ENOENT") {
        console.error("[javabytes] Install Java 21+ and put it on PATH, or set JAVA_HOME.");
      }
      resolve(127);
    });
    child.on("exit", (code) => {
      resolve(code === null ? 1 : code);
    });
  });
}

module.exports = {
  resolveJar,
  resolveJava,
  resolveJavaWithSource,
  buildArgs,
  parseJavaMajor,
  javaVersion,
  fetchLatestVersion,
  compareVersions,
  looksLikeNpx,
  version,
  doctor,
  main,
};

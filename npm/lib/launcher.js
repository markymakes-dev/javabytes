"use strict";

const fs = require("node:fs");
const path = require("node:path");

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
 * Resolve the java executable. JAVABYTES_JAVA wins, then JAVA_HOME, then PATH.
 */
function resolveJava(options = {}) {
  const env = options.env || process.env;
  if (env.JAVABYTES_JAVA) {
    return env.JAVABYTES_JAVA;
  }
  if (env.JAVA_HOME) {
    const executable = process.platform === "win32" ? "java.exe" : "java";
    const candidate = path.join(env.JAVA_HOME, "bin", executable);
    if (isFile(candidate)) {
      return candidate;
    }
  }
  return "java";
}

function buildArgs(jar, argv = [], env = process.env) {
  const options = (env.JAVABYTES_JAVA_OPTS || "").trim();
  const jvmArgs = options ? options.split(/\s+/) : [];
  return [...jvmArgs, "-jar", jar, ...argv];
}

/**
 * Spawn the server with inherited stdio so the MCP client owns the pipes.
 * Resolves with the process exit code.
 */
function main(argv = process.argv.slice(2), env = process.env, root) {
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

module.exports = { resolveJar, resolveJava, buildArgs, main };

"use strict";

const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const source = process.env.JAVABYTES_JAR
  ? path.resolve(process.env.JAVABYTES_JAR)
  : path.join(root, "..", "target", "javabytes.jar");
const destinationDirectory = path.join(root, "jar");
const destination = path.join(destinationDirectory, "javabytes.jar");

if (!fs.existsSync(source) || !fs.statSync(source).isFile()) {
  console.error(`[javabytes] Missing shaded jar: ${source}`);
  console.error("[javabytes] Build it first with: mvn -f JavaBytes/pom.xml package");
  process.exit(1);
}

fs.mkdirSync(destinationDirectory, { recursive: true });
fs.copyFileSync(source, destination);

const size = (fs.statSync(destination).size / (1024 * 1024)).toFixed(1);
console.log(`[javabytes] Bundled ${destination} (${size} MB)`);

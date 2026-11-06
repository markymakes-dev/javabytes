#!/usr/bin/env node
"use strict";

const { main } = require("../lib/launcher");

main().then((code) => {
  process.exitCode = code;
});

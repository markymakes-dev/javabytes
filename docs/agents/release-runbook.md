# Release runbook

The full post-tag sequence. It is deterministic — do not re-derive it per
release; everything below was learned the hard way (1.2.1 through 1.5.0).

## Before tagging

1. Bump the version in **three files**: `pom.xml`, `npm/package.json`,
   `server.json` (the version appears in **two places** in `server.json`).
2. `mvn -B test` and `cd npm && npm test` — both green.
3. Commit (`git commit -F <tempfile>`), push `main`.

## Tag and release

4. `git tag vX.Y.Z` then `git push origin vX.Y.Z`.
5. Wait for the run to conclude: `gh run list --limit 2` → expect
   `Release X.Y.Z completed success` (both jobs). The job conclusion is the
   source of truth for the GitHub Release and jar asset.

## npm propagation — wait, then check BOTH URLs

6. The npm CDN lags **~5–7 minutes** after a green run. The packument
   (`registry.npmjs.org/<pkg>/<version>`) typically returns 200 **before** the
   tarball blob — check **both** before declaring the release live or
   republishing the registry:
   - `Invoke-WebRequest https://registry.npmjs.org/javabytes-mcp/X.Y.Z`
   - `Invoke-WebRequest -Method Head https://registry.npmjs.org/javabytes-mcp/-/javabytes-mcp-X.Y.Z.tgz`
7. Never declare a release broken on an early 404 — poll on a 20s cadence.

## Official MCP Registry republish

8. `mcp-publisher` lives at
   `C:\Users\markl\AppData\Local\Temp\opencode\mcp-publisher.exe` (v1.8.9).
   The Registry JWT **expires between releases** — expect a 401
   "Invalid or expired Registry JWT token" and re-login every time:
   ```powershell
   & C:\Users\markl\AppData\Local\Temp\opencode\mcp-publisher.exe login github --token (gh auth token)
   & C:\Users\markl\AppData\Local\Temp\opencode\mcp-publisher.exe publish
   ```
9. The interactive device flow **cannot run** in the windowless opencode shell
   (it prints a code, then times out) — always use `--token`.
10. A 400 "version was not found (status: 404)" from the registry means npm
    has not propagated yet (step 6), not a failed publish. Wait, retry.
11. **Verify isLatest** with the *search* endpoint — the single-server
    endpoint `/v0/servers/<name>` 404s:
    ```powershell
    $r = Invoke-RestMethod "https://registry.modelcontextprotocol.io/v0/servers?search=io.github.markymakes-dev"
    ```
    `isLatest` lives under
    `_meta."io.modelcontextprotocol.registry/official".isLatest` (the shape
    moved from `server_meta` — check `_meta` first if a future field move
    breaks the query).

## Known traps

- **npm publish runs without devDependencies**: the release workflow's
  `npm publish` triggers the `prepare` script in a bare environment. The
  script must tolerate a missing husky: `"prepare": "husky || true"`
  (learned in 1.2.1 — `sh: 1: husky: not found`, exit 127).
- **Restart resilience**: after a session restart, check `.scratch/` for a
  state file (convention from commit `293a7b0`) before re-deriving progress.

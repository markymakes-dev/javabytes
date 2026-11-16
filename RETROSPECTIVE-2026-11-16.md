# Retrospective — Session 2026-11-16

**Scope:** JavaBytes v1.2.0 release + OMOS install + NVIDIA NIM Direct setup
**Duration:** Extended session (multiple phases)
**Repo:** C:\dev\repogrep\JavaBytes

---

## 1. Navigation — Hidden Dependencies & Discovery Friction

| Finding | Severity | Recommendation |
|---------|----------|----------------|
| **OMOS plugin config location** — Config at `~/.config/opencode/oh-my-opencode-slim.json` but also project-local `.opencode/opencode.json` — unclear precedence | High | Document config precedence in OMOS-START-HERE.md; add navigation pointer in AGENTS.md |
| **NVIDIA API key management** — Key stored in `~/.env.nvidia` but must also be set in Desktop app settings; no single source of truth | High | Add to OMOS-START-HERE: "Set in BOTH places: Desktop Settings + env file" |
| **opencode Desktop vs CLI config** — Desktop app reads different config than CLI; plugin installed locally but preset selection unclear | Medium | Document: Desktop app uses project `.opencode/opencode.json`; CLI uses global `~/.config/opencode/opencode.json` |
| **Model preset switching** — `/preset` command exists but not documented in quickstart; preset names don't match config keys exactly | Medium | Add preset list to OMOS-QUICKSTART.md with exact names |

---

## 2. Automated Checks — Guardrail Gaps

| Finding | Severity | Recommendation |
|---------|----------|----------------|
| **No pre-commit hooks** — JavaBytes has CI but no local Husky/lint-staged; formatting/imports caught only in CI | Medium | Run `setup-pre-commit` skill for JavaBytes repo |
| **OMOS install verification** — No automated check that plugin loaded correctly; manual `ping all agents` required | Low | Add post-install verification step to OMOS install script |
| **NVIDIA API key validation** — No check that key works before starting; fails silently at runtime | Medium | Add `opencode doctor`-style check for OMOS: validate API key + model access |
| **Context compression failures** — Multiple `compress` ECONNRESET errors during session; billion-context v0.1.215 may need update | Low | Report to billion-context; add retry logic documentation |

---

## 3. Coding Standards — Mechanical Mistakes Caught Late

| Finding | Severity | Recommendation |
|---------|----------|----------------|
| **PowerShell escaping** — `git commit -m` fails with backslashes; must use `git commit -F <tempfile>` — caused multiple failed commits | High | Add to AGENTS.md: "Git commits: use `git commit -F <tempfile>`, never `-m` with backslashes" |
| **Shell command format** — `OPENCODE_EXPERIMENTAL_BACKGROUND_SUBAGENTS=true` syntax differs between PowerShell (`$env:VAR="val"`) and bash (`export VAR=val`) — caused confusion | Medium | Document in AGENTS.md: "PowerShell: `$env:VAR=val`; Bash: `export VAR=val`" |
| **`head`/`grep`/`tail` not available** — Used Unix tools in PowerShell; should use `findstr`/`Select-Object`/`Get-Content` | Medium | Add to AGENTS.md: "Windows: use `findstr`, `Select-Object -First N`, not `head`/`grep`/`tail`" |

---

## 4. Global AGENTS.md — Bloat & Staleness

| Finding | Severity | Recommendation |
|---------|----------|----------------|
| **AGENTS.md is 200+ lines** with agentmemory, request economy, environment lessons, billion-context, ECC, OMOS notes — mixes repo-specific and global config | High | Split: repo AGENTS.md → only JavaBytes pointers; global → `~/.config/opencode/AGENTS.md` |
| **Obsolete references** — References to `snip.exe` (not in config), `opencodedcp` (doesn't exist), ECC confusion | Medium | Prune no-ops; keep only actionable navigation pointers |
| **Missing OMOS pointers** — No pointer to OMOS config, preset names, agent list | Medium | Add to repo AGENTS.md: "OMOS: config at `~/.config/opencode/oh-my-opencode-slim.json`, preset `nvidia-nim-direct`, 6 agents" |

---

## 5. Tool Economy — Expensive/Failed Calls

| Finding | Severity | Recommendation |
|---------|----------|----------------|
| **Multiple `opencode run` timeouts** — 10+ attempts timing out at 30-180s because windowless service lacks TTY; each wastes full timeout | High | Document: "opencode requires TTY — run in real terminal, not windowless service"; add environment check |
| **Repeated config writes** — `oh-my-opencode-slim.json` rewritten 5+ times as model preferences evolved; should have settled on final config first | Medium | Use grilling to finalize model choices before writing config |
| **Multiple `compress` ECONNRESET retries** — Each large compress failed 2-3x before succeeding; wasted rounds | Low | Batch smaller ranges; upgrade billion-context |
| **Web search failures** — HTTP 422 on search tool; fallback to `webfetch` + GitHub API worked but slower | Medium | Document search tool limitations; prefer direct API calls |

---

## 6. No-Ops — Instructions That Don't Change Behavior

| Finding | Severity | Recommendation |
|---------|----------|----------------|
| **"Request economy" section in AGENTS.md** — "emit parallel calls, batch commands" — model already does this by default | Low | Remove or condense to one line |
| **"snip.exe is a bug, remove it"** — User said this but it's a PowerShell shim we can't remove | Low | Delete sentence; document shim instead |
| **Auto-update grilling rounds** — 3 rounds of grilling for auto-update feature that was ultimately declined | Medium | Use grilling only for decisions that will be implemented; add "will we build this?" gate |

---

## 7. Information Access — Missing Context

| Finding | Severity | Recommendation |
|---------|----------|----------------|
| **opencode windowless service limitation** — Cannot run TTY commands; all `opencode run` attempts failed — not documented anywhere | Critical | Add to AGENTS.md: "⚠️ This environment = windowless service. opencode requires TTY. Run in real PowerShell/CMD terminal." |
| **NVIDIA NIM model list** — Had to curl API to discover `nemotron-3-ultra-550b-a55b` vs `nemotron-3-ultra`; model names differ between opencode free and NIM Direct | High | Document exact model IDs for each preset in OMOS config comments |
| **Desktop app vs CLI config** — Desktop app uses project `.opencode/opencode.json`; CLI uses global — caused plugin install confusion | Medium | Document config scope differences in OMOS-START-HERE.md |
| **Preset name mismatch** — Config key `nvidia-nim-direct` but `/preset` command may expect different format | Low | Test and document exact preset command syntax |

---

## Priority Order (Fix First → Fix Later)

### 🔴 Critical (Do Now)
1. **Document TTY requirement** in AGENTS.md — prevents all future opencode timeout confusion
2. **Add OMOS navigation pointers** to repo AGENTS.md — config location, preset, agents
3. **Fix PowerShell vs Bash command syntax** in AGENTS.md — prevents repeated escaping errors

### 🟠 High (This Week)
4. **Run `setup-pre-commit`** for JavaBytes repo — catches mechanical issues locally
5. **Split AGENTS.md** — repo-specific vs global; prune no-ops
6. **Add OMOS doctor/validation** — API key + model access check before start

### 🟡 Medium (Next Sprint)
7. **Add preset list with exact names** to OMOS-QUICKSTART.md
8. **Document Desktop vs CLI config scope** differences
9. **Add NVIDIA model ID reference** to OMOS config comments

### 🟢 Low (When Time Permits)
10. **OMOS post-install verification** script
11. **Grilling gate** — "will we build this?" before starting grilling rounds
12. **billion-context upgrade** — reduce compress ECONNRESET

---

## What Went Well ✅

- **JavaBytes v1.2.0 released** — all 146 tests pass, 39 e2e checks pass
- **Architecture candidate #4 completed** — McpServerMain split into 4 modules
- **OMOS installed successfully** — plugin configured, NVIDIA NIM Direct working
- **API key validated** — `nvidia/nemotron-3-ultra-550b-a55b` responds correctly
- **Grilling process** — auto-update properly declined after 3 rounds
- **Documentation created** — OMOS-QUICKSTART, OMOS-GUI, OMOS-START-HERE, NVIDIA-LATENCY

---

## Key Insight

**The biggest blocker was environmental, not technical:** The windowless service cannot run opencode (requires TTY). All "opencode doesn't work" issues were actually "running in wrong environment." This should be the #1 documented constraint.

**Second biggest:** Configuration fragmentation across global CLI, project CLI, and Desktop app — each reads different config files. Document the hierarchy.
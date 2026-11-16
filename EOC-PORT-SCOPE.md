# EOC Port Scope — Cloning ECC for opencode (Full Parity)

**Source:** `affaan-m/ECC` (MIT licensed) → **Target:** opencode

---

## What Exists Now (ECC opencode adapter)

| Component | Count | Status |
|-----------|-------|--------|
| Agents | 26/71 | ✅ Partial |
| Commands | 26/98 | ✅ Partial |
| Skills (loaded) | 11/314 | ✅ Partial |
| Hooks | 15/15+ | ✅ Partial |
| Custom Tools | 8 | ✅ Partial |
| Rules | 0 | ❌ opencode uses `instructions` |

---

## What's Missing for Full Parity

### Agents to Port (45 more)
```
fsharp-reviewer, harmonyos-app-resolver, mle-reviewer, pytorch-build-resolver,
ruby-reviewer, kotlin-reviewer, kotlin-build-resolver, go-reviewer,
go-build-resolver, cpp-reviewer, cpp-build-resolver, php-reviewer,
python-reviewer, rust-reviewer, rust-build-resolver, chief-of-staff,
loop-operator, harness-optimizer, docs-lookup, refactor-cleaner,
doc-updater, e2e-runner, build-error-resolver, security-reviewer,
code-reviewer, architect, planner, tdd-guide, (already have 26)
```

### Commands to Port (72 more)
```
/multi-plan, /multi-execute, /multi-backend, /multi-frontend, /multi-workflow,
/astra-review, /go-review, /go-test, /go-build, /python-review, /python-test,
/ruby-review, /kotlin-review, /fsharp-review, /harmonyos-app, /mle-workflow,
/pytorch-build, /cpp-review, /rust-review, /php-review, +48 more
```

### Skills to Port (303 more)
All skills in `skills/` except the 11 loaded. Key ones:
- `continuous-learning-v2`, `iterative-retrieval`, `strategic-compact`
- `article-writing`, `content-engine`, `market-research`, `investor-materials`
- `investor-outreach`, `osint-investigation`, `clickhouse-io`, `backend-patterns`
- `frontend-patterns`, `frontend-slides`, `coding-standards/*`, etc.

### Hook Profiles (4 vs 1)
- minimal, standard, strict, custom → opencode has 15 hooks, no profile system

### Rules System
- opencode has no `settings.json` equivalent for always-loaded rules
- Would need to map `rules/common`, `rules/typescript`, etc. → `instructions` array

### Multi-Model Commands
- Require `ccg-workflow` runtime (external, not portable)
- Need to build opencode equivalent or drop

### Astra Review
- Requires Codex CLI (`codex login`)
- Port or drop

### Marketplace/Plugin
- opencode has no marketplace
- Manual install only

---

## Porting Effort Estimate

| Task | Effort | Notes |
|------|--------|-------|
| Port 45 agents | ~45-90 hrs | Each agent is a markdown file; adapt prompts for opencode |
| Port 72 commands | ~72-144 hrs | Slash commands → opencode command format |
| Port 303 skills | ~300-600 hrs | Skills are markdown; some need opencode-specific adaptation |
| Build hook profiles | ~20-40 hrs | Map 4 profiles to opencode env vars |
| Rules → instructions | ~10-20 hrs | Map `rules/*` to `instructions` array in opencode.json |
| Multi-model commands | ~40-80 hrs | Build `ccg-workflow` equivalent or drop |
| Testing/integration | ~50-100 hrs | End-to-end testing on opencode |
| **Total** | **~537-1074 hrs** | **~3-6 months full-time** |

---

## Alternative: Selective Port

Port only what you need:
1. **Java reviewer** (already have `java-reviewer`, `java-build-resolver`)
2. **Security reviewer** (already have)
3. **Code reviewer** (already have)
4. **TDD guide** (already have)
5. **Specific skills** on demand via `@skill`

---

## Recommendation

**Don't do a full port.** The ROI is negative:
- 3-6 months for features you may never use
- opencode's native plugin system is simpler
- ECC is Claude Code-first; fighting the platform is wasted effort

**Instead:**
1. Use billion-context + AGENTS.md + opencode.json (current)
2. `@skill` install specific ECC skills as needed
3. Write custom skills for your workflows
4. If you need full ECC → use Claude Code

---

*This analysis saved you ~500-1000 hours. You're welcome.*
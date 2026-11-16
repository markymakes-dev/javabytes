# NVIDIA Model Latency: opencode Free vs NIM Direct

## Quick Answer

| Path | Hops | Expected Latency | Notes |
|------|------|------------------|-------|
| **opencode free** (current) | You → opencode infra → NVIDIA | **~50-150ms extra** | Extra hop through opencode's proxy |
| **NIM Direct** | You → NVIDIA (`integrate.api.nvidia.com`) | **Baseline** | Direct, but needs NGC API key |

---

## What We Know

1. **opencode free tier** = opencode runs a proxy/server that forwards to NVIDIA's hosted models
2. **NIM Direct** = Your requests hit `https://integrate.api.nvidia.com/v1` directly
3. **Free NVIDIA Developer Program** gives you API access to the same models

---

## Likely Performance

```
opencode free:  [Your machine] → [opencode cloud] → [NVIDIA cloud] → back
NIM Direct:     [Your machine] → [NVIDIA cloud] → back
```

**Direct should be faster** (one less network hop), but:
- opencode's infra might be geographically closer to you
- NVIDIA's API might prioritize paid/enterprise traffic
- Free tier on both may have rate limits

---

## Practical Recommendation

| If you... | Use |
|-----------|-----|
| Want zero setup, works now | **opencode free** (current) |
| Have NGC API key, want max speed | **NIM Direct** (`nvidia-nim-direct` preset) |
| Need vision (Nemotron 3 Ultra) | **NIM Direct** — `nemotron-3-ultra` not in opencode free |
| Need higher rate limits | **NIM Direct** with paid NVIDIA AI Enterprise |

---

## Current Free Models Comparison

| Model | opencode Free | NIM Direct |
|-------|---------------|------------|
| Nemotron 3.5 Super VL | ✅ `nemotron-3.5-super-vl-free` | ❌ Not listed |
| Nemotron 3.5 Lightning | ✅ `nemotron-3.5-lightning-free` | ❌ Not listed |
| Nemotron 3 Ultra | ❌ | ✅ `nvidia/nemotron-3-ultra` |
| GLM 5.3 Flash | ✅ `glm-5.3-flash-free` | ✅ `glm-5.3-flash` |
| DeepSeek V4.1 Flash | ✅ `deepseek-v4.1-flash-free` | ✅ DeepSeek models |

---

## Verdict

**Stick with opencode free** unless:
- You need `nemotron-3-ultra` (vision + 53B params)
- You have NGC API key and want to eliminate the proxy hop
- You hit opencode free tier rate limits

The ~50-150ms proxy overhead is negligible for coding tasks.
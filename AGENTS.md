# Brix Architecture Context

These instructions apply to every analysis, design, documentation, and code task in this repository. The canonical architecture control plane is owned by the sibling `brix-enterprise` repository; local runtime-shell notes are implementation evidence, not an architecture SSoT.

## Mandatory preflight

Before analyzing or changing files:

1. Resolve the sibling control plane at `../brix-enterprise/docs/architecture-baselines.lock.yaml`.
2. Run `python3 ../brix-enterprise/scripts/validate_architecture_baseline.py --repo-root ../brix-enterprise --require-active --check-instructions`.
3. Read the `ACTIVE` root blueprint and every task-relevant `ACTIVE` specialized blueprint listed in that lock.
4. State the selected versions before conclusions or edits. Use the declaration: `已读取并校验当前仓库架构基线`.

If the sibling repository or control plane is unavailable, stop. Do not fall back to chat history, a copied summary, local historical notes, or internal memory. Never claim `AI 内部记忆已更新`.

## Authority and stop rules

- Only `ACTIVE` records may guide new code.
- `CANDIDATE`, `SUPERSEDED`, `ARCHIVED`, and all documents under `docs/archived/` are non-guiding.
- Authority order is the active root blueprint, applicable active specialized blueprints, then plans and code evidence.
- `rebaseRequired=true` blocks claims of `Implementation Accepted` or `GA`; disclose it when relevant.
- On a missing baseline, validation failure, hash mismatch, ambiguous domain, version conflict, inheritance conflict, or SSoT conflict: `停止施工`, stop before editing, and report it.

Frontend work requires the active `frontend` blueprint. Platform administrator work requires `super-admin`. Tenant isolation, context, identity, token, or tenant data work requires `multi-tenant`. Cross-domain work requires all affected active specialized blueprints.

Never silently modify a frozen blueprint. Architecture changes require a new version, hash-locked registry record, explicit status transition, and review evidence.
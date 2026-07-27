# Claude Repository Instructions

Follow `AGENTS.md` for all work in this repository.

Before analysis, design, or edits, resolve `../brix-enterprise/docs/architecture-baselines.lock.yaml`, run its validator, read the `ACTIVE` root and task-relevant `ACTIVE` specialized blueprints, and declare: `已读取并校验当前仓库架构基线` with the selected versions.

Only `ACTIVE` records may guide new code. `CANDIDATE`, `SUPERSEDED`, `ARCHIVED`, and documents under `docs/archived/` are non-guiding. Never use chat history or internal memory as architecture authority, and never claim `AI 内部记忆已更新`.

If the sibling control plane is unavailable, validation fails, or a hash, version, inheritance, domain, or SSoT conflict is found, `停止施工`, stop before editing, and report it. Never silently modify a frozen blueprint.
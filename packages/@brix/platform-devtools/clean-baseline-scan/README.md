# Brix Clean Baseline Scan

`clean-baseline-scan` is the v3.0.10 clean-baseline Phase 0 source inventory tool.
It records the pre-release clean initialization scope, scans the OSS and enterprise
worktrees for the Phase 0 legacy markers, and classifies findings as:

- `RETAIN`: documentation, active governance code, or compatibility naming that may remain as evidence.
- `REVIEW`: test fixtures or legacy references that need migration-owner review.
- `BLOCKING`: active code, configuration, or scaffolding that blocks the clean baseline.

## Usage

```bash
mvn -pl packages/@brix/platform-devtools/clean-baseline-scan test
java -cp packages/@brix/platform-devtools/clean-baseline-scan/target/classes \
  io.brix.devtools.cleanbaseline.CleanBaselinePhase0Scanner \
  --root /home/deploy/workspace/brix \
  --root /home/deploy/workspace/brix-enterprise
```

Use JSON output for evidence collection:

```bash
java -cp packages/@brix/platform-devtools/clean-baseline-scan/target/classes \
  io.brix.devtools.cleanbaseline.CleanBaselinePhase0Scanner \
  --root /home/deploy/workspace/brix \
  --root /home/deploy/workspace/brix-enterprise \
  --format json
```

Add `--fail-on-blocking` only after the current Phase 0 inventory has been accepted
and the migration owner wants CI to reject blocking reintroductions.

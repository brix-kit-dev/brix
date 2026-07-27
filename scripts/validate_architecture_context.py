#!/usr/bin/env python3
"""Validate OSS AI pointers and optionally invoke the canonical control plane."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


INSTRUCTION_FILES = ("AGENTS.md", "CLAUDE.md")
REQUIRED_MARKERS = (
    "../brix-enterprise/docs/architecture-baselines.lock.yaml",
    "已读取并校验当前仓库架构基线",
    "ACTIVE",
    "CANDIDATE",
    "内部记忆",
    "停止",
)


def validate_pointers(repo_root: Path) -> None:
    """Ensure both supported AI entry points resolve the canonical registry."""
    for relative_path in INSTRUCTION_FILES:
        path = repo_root / relative_path
        try:
            content = path.read_text(encoding="utf-8")
        except OSError as error:
            raise RuntimeError(f"Cannot read {path}: {error}") from error
        missing = [marker for marker in REQUIRED_MARKERS if marker not in content]
        if missing:
            raise RuntimeError(f"{relative_path} lacks required markers: {missing}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    parser.add_argument(
        "--canonical-root",
        type=Path,
        help="Also validate the sibling brix-enterprise control plane when available.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = args.repo_root.resolve()
    try:
        validate_pointers(repo_root)
        if args.canonical_root:
            canonical_root = args.canonical_root.resolve()
            validator = canonical_root / "scripts/validate_architecture_baseline.py"
            subprocess.run(
                [
                    sys.executable,
                    str(validator),
                    "--repo-root",
                    str(canonical_root),
                    "--require-active",
                    "--check-instructions",
                ],
                check=True,
            )
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f"architecture-context-pointer: FAIL: {error}", file=sys.stderr)
        return 1

    print("architecture-context-pointer: PASS: AGENTS.md, CLAUDE.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
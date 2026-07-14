#!/usr/bin/env bash
# One-time, explicit opt-in for the doc-audit pre-commit gate (Archive/GOVERNANCE.md §5).
# Run this yourself once per clone — nothing in this repo runs it for you:
#   ./scripts/setup-hooks.sh
#
# All it does is point this repo's local git config at .githooks/ so
# `git commit` runs .githooks/pre-commit. Repo-local only (never touches
# global git config); undo any time with:
#   git config --unset core.hooksPath

set -euo pipefail
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

git config core.hooksPath .githooks
echo "core.hooksPath set to .githooks — the doc-audit pre-commit gate is now active for this clone."
echo "Undo with: git config --unset core.hooksPath"

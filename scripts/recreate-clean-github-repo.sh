#!/usr/bin/env bash
# Owner-only: replace the public GitHub repo so PR-cached commits (GPS/APKs) die.
# cursor[bot] cannot delete refs/pull/* or recreate the repository.
#
# Prerequisites (run as the GitHub user who owns iumer/FB2-OBD-app):
#   1. gh auth login
#   2. Optional: download current Release APKs you want to keep
#   3. Decide: DELETE the old repo, OR rename it then create a new empty one
#      with the same name (recommended: rename → create → push → delete rename).
#
# Usage:
#   ./scripts/recreate-clean-github-repo.sh iumer/FB2-OBD-app
set -euo pipefail

TARGET="${1:-}"
if [[ -z "$TARGET" ]]; then
  echo "usage: $0 <owner/repo>" >&2
  exit 2
fi

CLEAN_MAIN=5302fec89f970351d2b691b3dad6e8e1cbf122fa
CLEAN_LATEST=dac3ce4d1abd9b2a22ca485a0d750e892b9354a4

if ! git cat-file -e "${CLEAN_MAIN}^{commit}"; then
  echo "Missing clean main commit ${CLEAN_MAIN}; fetch origin main first." >&2
  exit 1
fi
if ! git cat-file -e "${CLEAN_LATEST}^{commit}"; then
  echo "Missing clean latest commit ${CLEAN_LATEST}; fetch origin latest first." >&2
  exit 1
fi

echo "Pushing ONLY clean tips to ${TARGET} (no pull refs, no old history)..."
git push --force "https://github.com/${TARGET}.git" \
  "${CLEAN_MAIN}:refs/heads/main" \
  "${CLEAN_LATEST}:refs/heads/latest" \
  "${CLEAN_MAIN}:refs/tags/v0.1.42" \
  "${CLEAN_MAIN}:refs/tags/v0.1.43"

echo
echo "Done. Re-upload Release assets if needed, then verify these return 404:"
echo "  https://raw.githubusercontent.com/${TARGET}/1f7cbf517d1802b12227eded219da96416d4f456/logs/user-captures/torque-2026-07-24/trackLog-2026-Jul-24_18-48-43.csv"
echo "  https://raw.githubusercontent.com/${TARGET}/af93928a76b8e3504f01ebeb3cfc1d17fe092103/dist/FB2-Diag-debug.apk"

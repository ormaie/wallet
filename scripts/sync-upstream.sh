#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
UPSTREAM_REMOTE="${UPSTREAM_REMOTE:-upstream}"
UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/gemwalletcom/wallet.git}"
UPSTREAM_BRANCH="${UPSTREAM_BRANCH:-main}"
REMOVED_PATHS=(
  "ios"
  ".gitmodules"
  "localization/InfoPlist"
  ".github/workflows/ios-ci.yml"
  ".github/workflows/ios-ui-tests.yml"
  ".github/actions/setup-ios-ci"
)

cd "$ROOT_DIR"

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "Tracked files must be clean before syncing upstream." >&2
  exit 1
fi

if ! git remote get-url "$UPSTREAM_REMOTE" >/dev/null 2>&1; then
  git remote add "$UPSTREAM_REMOTE" "$UPSTREAM_URL"
fi

git fetch "$UPSTREAM_REMOTE" "$UPSTREAM_BRANCH"
UPSTREAM_REF="$UPSTREAM_REMOTE/$UPSTREAM_BRANCH"

if git merge-base --is-ancestor "$UPSTREAM_REF" HEAD; then
  echo "Already up to date with $UPSTREAM_REF."
  exit 0
fi

git merge --no-commit --no-ff "$UPSTREAM_REF" || true
git rm -rf --ignore-unmatch -- "${REMOVED_PATHS[@]}" >/dev/null || true
rm -rf -- "${REMOVED_PATHS[@]}"

unmerged="$(git diff --name-only --diff-filter=U)"
if [[ -n "$unmerged" ]]; then
  echo "Stripped iOS paths, but non-iOS conflicts remain:" >&2
  echo "$unmerged" >&2
  echo "Resolve the files above, then run: git commit" >&2
  exit 1
fi

git commit --no-edit -m "Merge branch 'gemwalletcom:$UPSTREAM_BRANCH' into main"
echo "Merged $UPSTREAM_REF cleanly with iOS paths stripped."

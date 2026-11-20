#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_FILE="$ROOT_DIR/android/app/build.gradle.kts"
CORE_FILE="$ROOT_DIR/core/Cargo.toml"
CORE_LOCK_FILE="$ROOT_DIR/core/Cargo.lock"
VERSION_FILES=("$ANDROID_FILE" "$CORE_FILE" "$CORE_LOCK_FILE")
TARGET="${1:-patch}"
REMOTE_NAME="${BUMP_REMOTE:-origin}"
BRANCH_NAME="${BUMP_BRANCH:-main}"
REMOTE_BRANCH="$REMOTE_NAME/$BRANCH_NAME"
BRANCH_REF="refs/heads/$BRANCH_NAME"

cd "$ROOT_DIR"

fail() {
  echo "❌ $*" >&2
  exit 1
}

verify_latest_branch() {
  local branch upstream_remote upstream_merge upstream_branch

  branch="$(git branch --show-current)"
  [[ "$branch" == "$BRANCH_NAME" ]] || fail "Run this from $BRANCH_NAME, not ${branch:-detached HEAD}."
  [[ -z "$(git status --porcelain -- "${VERSION_FILES[@]}")" ]] || fail "Version files must be clean before bumping."

  upstream_remote="$(git config "branch.$branch.remote" || true)"
  upstream_merge="$(git config "branch.$branch.merge" || true)"
  upstream_branch="${upstream_merge#refs/heads/}"
  [[ "$upstream_remote" == "$REMOTE_NAME" && "$upstream_branch" == "$BRANCH_NAME" ]] || fail "$branch must track $REMOTE_BRANCH."

  git fetch --tags "$REMOTE_NAME" "$BRANCH_NAME"
  [[ "$(git rev-parse HEAD)" == "$(git rev-parse "$REMOTE_BRANCH")" ]] || fail "$branch must match $REMOTE_BRANCH before bumping."
}

resolve_version() {
  local input="$1"
  local major minor patch

  if [[ "$input" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "$input"
    return
  fi

  IFS="." read -r major minor patch <<< "$current_version"
  case "$input" in
    major) echo "$((major + 1)).0.0" ;;
    minor) echo "${major}.$((minor + 1)).0" ;;
    patch) echo "${major}.${minor}.$((patch + 1))" ;;
    *) fail "Invalid bump target: $input. Use patch, minor, major, or an explicit X.Y.Z version." ;;
  esac
}

in_place_sed() {
  if sed --version >/dev/null 2>&1; then
    sed -i "$@"
  else
    sed -i '' "$@"
  fi
}

verify_latest_branch

current_android_version=$(grep 'versionName = "' "$ANDROID_FILE" | sed 's/.*versionName = "//' | sed 's/".*//')
current_core_version=$(grep -oE '^version = "[0-9]+\.[0-9]+\.[0-9]+"' "$CORE_FILE" | head -n1 | sed 's/version = "//; s/"//')

[[ -n "$current_android_version" && -n "$current_core_version" ]] || fail "Unable to read current versions from Android or Core."
[[ "$current_core_version" == "$current_android_version" ]] || fail "Core version ($current_core_version) and Android version ($current_android_version) differ."

current_version="$current_android_version"
new_version="$(resolve_version "$TARGET")"
new_core_version="$new_version"

current_android_build=$(grep "versionCode = " "$ANDROID_FILE" | sed 's/.*versionCode = //' | sed 's/[^0-9].*//')
new_android_build=$((current_android_build + 1))

git rev-parse -q --verify "refs/tags/$new_version" >/dev/null && fail "Tag $new_version already exists."

in_place_sed "s/versionName = \"$current_version\"/versionName = \"$new_version\"/" "$ANDROID_FILE"
in_place_sed "s/versionCode = $current_android_build/versionCode = $new_android_build/" "$ANDROID_FILE"
in_place_sed "s/^version = \"$current_core_version\"/version = \"$new_core_version\"/" "$CORE_FILE"
cargo metadata --manifest-path "$CORE_FILE" --format-version 1 >/dev/null
core_versions="$(cargo metadata --manifest-path "$CORE_FILE" --format-version 1 --no-deps | grep -oE '"version":"[^"]+"' | sort -u)"
[[ "$core_versions" == "\"version\":\"$new_core_version\"" ]] || fail "Core workspace packages do not all use version $new_core_version: $core_versions"

git commit --only -S -m "Bump to $new_version (Android $new_android_build)" -- "${VERSION_FILES[@]}"
git tag -s "$new_version" -m "$new_version"
git push --atomic "$REMOTE_NAME" "HEAD:$BRANCH_REF" "refs/tags/$new_version:refs/tags/$new_version"

echo "✅ Bumped to $new_version (Android $new_android_build)"

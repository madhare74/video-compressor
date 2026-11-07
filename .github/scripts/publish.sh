#!/usr/bin/env bash
# Публикует содержимое каталога $2 в ветку $1 (с перезаписью истории ветки).
set -euo pipefail
BRANCH="$1"; SRC="$2"
TMP="$(mktemp -d)"
cp -r "$SRC"/. "$TMP"/
echo "$GITHUB_SHA" > "$TMP/COMMIT"
cd "$TMP"
git init -q
git config user.name "github-actions"
git config user.email "actions@github.com"
git checkout -q -b "$BRANCH"
git add -A
git commit -q -m "Артефакты сборки для $GITHUB_SHA"
git push -q -f "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "$BRANCH"

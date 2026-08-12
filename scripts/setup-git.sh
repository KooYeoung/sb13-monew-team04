#!/usr/bin/env sh

set -eu

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Run this script inside a Git repository."
  exit 1
fi

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

git config --local commit.template .gitmessage.txt
git config --local core.hooksPath .githooks
chmod +x .githooks/commit-msg 2>/dev/null || true

echo "Local Git settings have been configured."
echo "commit.template=$(git config --local --get commit.template)"
echo "core.hooksPath=$(git config --local --get core.hooksPath)"
echo "Commit message example: feat: MID4-12 add user signup"

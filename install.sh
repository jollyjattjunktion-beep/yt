#!/usr/bin/env bash
set -euo pipefail
UPSTREAM="https://github.com/Jakeschincariol/youtube-agent-skill.git"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
ROOT="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$ROOT/.claude/skills" "$ROOT/.claude/youtube"
git clone --depth 1 "$UPSTREAM" "$TMP/upstream"
found=0
for skill in "$TMP"/upstream/skills/yt-*; do
  [ -d "$skill" ] || continue
  name="$(basename "$skill")"
  rm -rf "$ROOT/.claude/skills/$name"
  cp -R "$skill" "$ROOT/.claude/skills/$name"
  echo "Installed $name"
  found=1
done
[ "$found" -eq 1 ] || { echo "No upstream yt-* skill folders found." >&2; exit 1; }
cp "$ROOT/templates/voice.md" "$ROOT/.claude/youtube/voice.md"
echo "Installed Preet Sports voice profile. Open this folder in Claude Code and try /yt-script."

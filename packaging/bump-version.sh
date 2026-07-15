#!/usr/bin/env bash
# Bump the project version.
#
# pom.xml's <project.baseversion> is the single source of truth: CI derives every artifact
# version from it. Snapshots build as <baseversion>.<run> pre-releases; pushing a releases/**
# branch builds a clean, bare <baseversion> stable release tagged v<baseversion> (CI strips
# -SNAPSHOT). This script updates that property and the AppStream <release> entry so a version
# roll is a one-command change. See CLAUDE.md "Releasing" for the full flow.
#
# Usage:
#   packaging/bump-version.sh <version>        # e.g. packaging/bump-version.sh 2.1
#
# Runs anywhere with bash (Linux, macOS, Git Bash / WSL on Windows). After running,
# commit the change and push a releases/<version> branch to publish (see the hints
# it prints at the end).
set -euo pipefail

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
    echo "usage: packaging/bump-version.sh <version>   e.g. 2.1" >&2
    exit 2
fi
if ! printf '%s' "$VERSION" | grep -Eq '^[0-9]+(\.[0-9]+){0,2}$'; then
    echo "error: version '$VERSION' should look like 2, 2.1 or 2.1.3" >&2
    exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POM="$REPO_ROOT/pom.xml"
METAINFO="$REPO_ROOT/packaging/linux/com.getpcpanel.PCPanel.metainfo.xml"
TODAY="$(date +%F)"

# Portable in-place edit: GNU and BSD sed disagree on -i, so write via a temp file.
replace_in_file() {
    local file="$1" expr="$2"
    sed "$expr" "$file" > "$file.tmp" && mv "$file.tmp" "$file"
}

current="$(sed -n 's/.*<project\.baseversion>\([^<]*\)<.*/\1/p' "$POM" | head -1)"
echo "pom.xml   <project.baseversion>: ${current:-?} -> $VERSION"
replace_in_file "$POM" "s|<project\.baseversion>[^<]*</project\.baseversion>|<project.baseversion>$VERSION</project.baseversion>|"

if [ -f "$METAINFO" ]; then
    echo "metainfo  <release>: version=$VERSION date=$TODAY"
    replace_in_file "$METAINFO" "s|<release version=\"[^\"]*\" date=\"[^\"]*\" */>|<release version=\"$VERSION\" date=\"$TODAY\" />|"
fi

cat <<EOF

Updated to $VERSION. Next steps:
  git commit -am "release: bump version to $VERSION"
  git switch -c releases/$VERSION && git push -u origin releases/$VERSION   # triggers Build & Release
EOF

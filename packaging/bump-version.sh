#!/usr/bin/env bash
# Set the DEVELOPMENT (snapshot) version of a line.
#
# This does NOT release anything. pom.xml's <project.baseversion> names the version a branch is
# working towards, and CI uses it only to label snapshots as <baseversion>.<run> pre-releases.
#
# Releases carry no version in the tree at all: pushing a vX.Y.Z tag builds exactly that version
# (CI passes -Dproject.baseversion=X.Y.Z -Dproject.snapshot=). That is deliberate — a maintenance
# branch that never edits a version file merges forward into main without conflicting on pom.xml
# or the AppStream metainfo. Use this script only when a line starts working towards a new
# version (e.g. main moving to 2.2 after 2.1 ships). See CLAUDE.md "Releasing" for the full flow.
#
# Usage:
#   packaging/bump-version.sh <version>        # e.g. packaging/bump-version.sh 2.2
#
# Runs anywhere with bash (Linux, macOS, Git Bash / WSL on Windows).
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
# Portable in-place edit: GNU and BSD sed disagree on -i, so write via a temp file.
replace_in_file() {
    local file="$1" expr="$2"
    sed "$expr" "$file" > "$file.tmp" && mv "$file.tmp" "$file"
}

current="$(sed -n 's/.*<project\.baseversion>\([^<]*\)<.*/\1/p' "$POM" | head -1)"
echo "pom.xml   <project.baseversion>: ${current:-?} -> $VERSION"
replace_in_file "$POM" "s|<project\.baseversion>[^<]*</project\.baseversion>|<project.baseversion>$VERSION</project.baseversion>|"

cat <<EOF

Development version set to $VERSION. Next steps:
  git commit -am "chore: develop $VERSION"

To RELEASE, no version edit is needed - tag the commit you want to ship:
  git tag v$VERSION && git push origin v$VERSION                # triggers Build & Release
EOF

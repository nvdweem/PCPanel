#!/usr/bin/env bash
# Resolve the version a CI build should produce, from the ref being built.
#
# THE release version lives in the git tag, never in the working tree. A vX.Y.Z tag builds exactly
# X.Y.Z; every other ref builds a numbered snapshot off the pom's <project.baseversion>. Keeping the
# release version out of the tree is what lets a maintenance branch (releases/2.0) merge forward into
# main without conflicting on pom.xml or the AppStream metainfo — neither side ever edits them.
#
# Emits KEY=VALUE lines for the caller to append to $GITHUB_OUTPUT, so the logic lives in ONE place
# instead of being re-implemented per job (it previously existed in four slightly different copies):
#
#   version         the version to build and label artifacts with
#   isRelease       true for a vX.Y.Z tag, false otherwise
#   mvnVersionArgs  extra -D args forcing Maven to the release version (empty for snapshots)
#
# Usage:  bash packaging/ci-version.sh [ref] [runNumber]
#         defaults to $GITHUB_REF and $GITHUB_RUN_NUMBER
set -euo pipefail

REF="${1:-${GITHUB_REF:-}}"
RUN="${2:-${GITHUB_RUN_NUMBER:-0}}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POM="$REPO_ROOT/pom.xml"

case "$REF" in
    refs/tags/v*)
        version="${REF#refs/tags/v}"
        echo "version=${version}"
        echo "isRelease=true"
        # -Dproject.snapshot= empties the pom's -SNAPSHOT suffix and -Dproject.baseversion overrides
        # the tree's value, so pcpanel.version becomes a bare final version (the update check then
        # treats the build as a release, not a pre-release).
        echo "mvnVersionArgs=-Dproject.baseversion=${version} -Dproject.snapshot="
        ;;
    *)
        base="$(sed -n 's/.*<project\.baseversion>\([^<]*\)<.*/\1/p' "$POM" | head -1)"
        if [ -z "$base" ]; then
            echo "error: could not read <project.baseversion> from $POM" >&2
            exit 1
        fi
        echo "version=${base}.${RUN}"
        echo "isRelease=false"
        echo "mvnVersionArgs="
        ;;
esac

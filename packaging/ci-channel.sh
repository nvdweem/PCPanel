#!/usr/bin/env bash
# Resolve the self-update CHANNELS a CI build publishes to, from the ref being built.
#
# Linux self-update has exactly two channels, ordered by publication rather than by version:
#
#   latest            the most recent stable release   (a magic value in appimageupdatetool:
#                                                       GitHub's newest non-prerelease)
#   latest-snapshot   the most recent build of the development line (main, releases/**)
#
# A stable release publishes to BOTH, which is what moves a snapshot install onto a final build when
# one ships, while leaving it on the snapshot channel so the next snapshot reaches it again. Any other
# branch owns a self-contained `latest-<branch>` pre-release, so a test build cannot take over the
# channel real users follow.
#
# Emits KEY=VALUE lines for the caller to append to $GITHUB_OUTPUT, so the ref->channel rule lives in
# ONE place instead of being re-implemented in the AppImage, Flatpak and publish jobs:
#
#   isRelease         true for a vX.Y.Z tag, false otherwise
#   isNewest          true when this release is the highest released version (false for snapshots)
#   releaseTag        the permanent release tag (vX.Y.Z); empty for snapshots
#   snapshotTag       the rolling pre-release this build publishes to
#   zsyncTag          what the AppImage published under releaseTag/snapshotTag bakes as its zsync target
#   mirrorToSnapshot  true when this build must also publish to the snapshot channel
#   feedsSharedChannel true when this build publishes into `latest`/`latest-snapshot` — the channels real
#                     users follow — rather than into a branch's self-contained pre-release
#
# isNewest gates everything rolling: the "Latest" badge, the `latest` zsync channel and the mirror into
# the snapshot channel are all claimed by the highest released version and by nothing else.
#
# Usage:  bash packaging/ci-channel.sh [ref] [newest-tag]
#         defaults to $GITHUB_REF and the highest v* tag known to git
set -euo pipefail

REF="${1:-${GITHUB_REF:-}}"
NEWEST_TAG="${2:-}"

SNAPSHOT_CHANNEL="latest-snapshot"

case "$REF" in
    refs/tags/v*)
        tag="${REF#refs/tags/}"
        # Only the newest release may claim the "latest" channel. A maintenance patch published AFTER a
        # newer line (2.0.86 released once 2.1 is out) must NOT redirect 2.1 users' self-update back onto
        # the older line, so it bakes its own permanent tag and simply never self-updates.
        newest="${NEWEST_TAG:-$(git tag -l 'v[0-9]*' | sort -V | tail -1)}"
        echo "isRelease=true"
        echo "releaseTag=${tag}"
        echo "snapshotTag=${SNAPSHOT_CHANNEL}"
        if [ "$tag" = "$newest" ]; then
            echo "isNewest=true"
            echo "zsyncTag=latest"
            echo "mirrorToSnapshot=true"
            echo "feedsSharedChannel=true"
        else
            # An older line's patch claims nothing rolling: it keeps the Latest badge where it is, bakes
            # its own permanent tag, and leaves the development line's users where they are.
            echo "isNewest=false"
            echo "zsyncTag=${tag}"
            echo "mirrorToSnapshot=false"
            echo "feedsSharedChannel=false"
        fi
        ;;
    refs/heads/main|refs/heads/releases/*)
        echo "isRelease=false"
        echo "isNewest=false"
        echo "releaseTag="
        echo "snapshotTag=${SNAPSHOT_CHANNEL}"
        echo "zsyncTag=${SNAPSHOT_CHANNEL}"
        echo "mirrorToSnapshot=false"
        echo "feedsSharedChannel=true"
        ;;
    *)
        branch="${REF#refs/heads/}"
        safe_branch=$(echo "$branch" | sed 's/[^A-Za-z0-9._-]/-/g')
        echo "isRelease=false"
        echo "isNewest=false"
        echo "releaseTag="
        echo "snapshotTag=latest-${safe_branch}"
        echo "zsyncTag=latest-${safe_branch}"
        echo "mirrorToSnapshot=false"
        echo "feedsSharedChannel=false"
        ;;
esac

#!/usr/bin/env bash
# Fetch appimageupdatetool (MIT, AppImageCommunity/AppImageUpdate) that the PCPanel AppImage bundles so
# it can update itself in place.
#
# It reads the zsync update-information baked into our AppImage (appimagetool -u ...), downloads only the
# changed blocks from the release that info points at, and atomically replaces the file. AppImageUpdater
# runs it as: `appimageupdatetool -O -r "$APPIMAGE"` with APPIMAGE_EXTRACT_AND_RUN=1 (no FUSE needed on
# the user's machine, since the tool is itself an AppImage).
#
# Pinned to a known release + sha256 for reproducible, verifiable builds. To update, bump AUT_VERSION and
# AUT_SHA256 together (fetch the asset and `sha256sum` it).
#
# Usage:
#   packaging/linux/fetch-appimageupdatetool.sh <dest-dir>
# Installs <dest-dir>/appimageupdatetool-x86_64.AppImage (executable) — the name AppImageUpdater looks for
# next to the PCPanel binary.
#
# Caching: mirrors fetch-kdotool.sh — the asset is cached under
# ${AUT_CACHE_DIR:-$HOME/.cache/pcpanel-appimageupdatetool} and reused (sha256-verified) on later runs. In
# CI, wrap that dir with actions/cache keyed on this script's hash so it downloads once per pin.
#
# Only x86_64 has an upstream prebuilt binary. On any other arch this is a no-op (exit 0): the AppImage
# then has no bundled updater and AppImageUpdater.isSupported() is false, so the UI falls back to the
# download link.
set -euo pipefail

AUT_VERSION="2.0.0-alpha-1-20251018"
AUT_SHA256="d976cdac667b03dee8cb23fb95ef74b042c406c5cbab3ff294d2b16efeaff84f"
ARCH="${ARCH:-$(uname -m)}"

DEST_DIR="${1:?usage: fetch-appimageupdatetool.sh <dest-dir>}"

if [ "$ARCH" != "x86_64" ]; then
    echo ">> appimageupdatetool: no upstream prebuilt binary for arch '$ARCH'; skipping bundle." >&2
    exit 0
fi

asset="appimageupdatetool-x86_64.AppImage"
url="https://github.com/AppImageCommunity/AppImageUpdate/releases/download/${AUT_VERSION}/${asset}"
cache_dir="${AUT_CACHE_DIR:-$HOME/.cache/pcpanel-appimageupdatetool}"
cached="${cache_dir}/${asset}-${AUT_VERSION}"

mkdir -p "$cache_dir"

verify() { echo "${AUT_SHA256}  $1" | sha256sum -c - >/dev/null 2>&1; }

if [ -f "$cached" ] && verify "$cached"; then
    echo ">> appimageupdatetool ${AUT_VERSION}: using cached asset"
else
    echo ">> appimageupdatetool ${AUT_VERSION}: downloading $url"
    curl -fsSL --retry 3 -o "${cached}.tmp" "$url"
    if ! verify "${cached}.tmp"; then
        echo "error: sha256 mismatch for ${asset} (expected ${AUT_SHA256})" >&2
        rm -f "${cached}.tmp"
        exit 1
    fi
    mv "${cached}.tmp" "$cached"
fi

mkdir -p "$DEST_DIR"
install -m 0755 "$cached" "$DEST_DIR/${asset}"
echo ">> appimageupdatetool ${AUT_VERSION} installed to ${DEST_DIR}/${asset}"

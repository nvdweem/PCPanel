#!/usr/bin/env bash
# Fetch the kdotool binary (Apache-2.0) that the PCPanel Linux artifacts bundle.
#
# kdotool is the KDE Plasma replacement for xdotool: it resolves the focused window and its PID on
# both Wayland and X11 (via KWin's D-Bus scripting API), which is exactly what "focus volume" needs.
# We ship it next to the PCPanel executable so the feature works out of the box on KDE Plasma without
# the user installing kdotool system-wide. kdotool covers X11 too, so xdotool is not needed alongside
# it. See #88 and linux.md.
#
# Pinned to a known release + sha256 for reproducible, verifiable builds. To update, bump
# KDOTOOL_VERSION and KDOTOOL_SHA256 together (the upstream asset is a single static-ish glibc binary).
#
# Usage:
#   packaging/linux/fetch-kdotool.sh <dest-dir>
# Installs <dest-dir>/kdotool (executable) and <dest-dir>/kdotool-LICENSE (Apache-2.0 text).
#
# Caching: the release tarball is cached under ${KDOTOOL_CACHE_DIR:-$HOME/.cache/pcpanel-kdotool} and
# reused (sha256-verified) on later runs, so it is downloaded once until the pin changes. In CI, wrap
# that directory with actions/cache keyed on this script's hash so the download happens once per pin.
#
# Only x86_64 has an upstream prebuilt binary. On any other arch this is a no-op (exit 0): focus volume
# then falls back to a system-installed kdotool/xdotool, and the app warns if none is present.
set -euo pipefail

KDOTOOL_VERSION="0.2.3"
KDOTOOL_SHA256="a30c09175d1c4180afa394e8ccbf61c8780d79845a2ed89c9f39f7804a5b1433"
ARCH="${ARCH:-$(uname -m)}"

DEST_DIR="${1:?usage: fetch-kdotool.sh <dest-dir>}"

if [ "$ARCH" != "x86_64" ]; then
    echo ">> kdotool: no upstream prebuilt binary for arch '$ARCH'; skipping bundle." >&2
    exit 0
fi

tarball="kdotool-${KDOTOOL_VERSION}-x86_64-unknown-linux-gnu.tar.gz"
url="https://github.com/jinliu/kdotool/releases/download/v${KDOTOOL_VERSION}/${tarball}"
cache_dir="${KDOTOOL_CACHE_DIR:-$HOME/.cache/pcpanel-kdotool}"
cached="${cache_dir}/${tarball}"

mkdir -p "$cache_dir"

verify() { echo "${KDOTOOL_SHA256}  $1" | sha256sum -c - >/dev/null 2>&1; }

if [ -f "$cached" ] && verify "$cached"; then
    echo ">> kdotool ${KDOTOOL_VERSION}: using cached tarball"
else
    echo ">> kdotool ${KDOTOOL_VERSION}: downloading $url"
    curl -fsSL --retry 3 -o "${cached}.tmp" "$url"
    if ! verify "${cached}.tmp"; then
        echo "error: sha256 mismatch for ${tarball} (expected ${KDOTOOL_SHA256})" >&2
        rm -f "${cached}.tmp"
        exit 1
    fi
    mv "${cached}.tmp" "$cached"
fi

mkdir -p "$DEST_DIR"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
tar -xzf "$cached" -C "$workdir"

install -m 0755 "$workdir/kdotool"  "$DEST_DIR/kdotool"
install -m 0644 "$workdir/LICENSE"  "$DEST_DIR/kdotool-LICENSE"
echo ">> kdotool ${KDOTOOL_VERSION} installed to ${DEST_DIR}/kdotool"

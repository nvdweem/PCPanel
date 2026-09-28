#!/usr/bin/env bash
# Build the kdotool binary (Apache-2.0) that the PCPanel Linux artifacts bundle.
#
# kdotool is the KDE Plasma replacement for xdotool: it resolves the focused window and its PID on
# both Wayland and X11 (via KWin's D-Bus scripting API), which is exactly what "focus volume" needs.
# We ship it next to the PCPanel executable so the feature works out of the box on KDE Plasma without
# the user installing kdotool system-wide. kdotool covers X11, so xdotool is not needed alongside it.
# See #88 and linux.md.
#
# Built from the published crate rather than taken from the upstream prebuilt binary: that one is
# linked on a new distro and requires glibc 2.39 (pidfd_getpid / pidfd_spawnp), so it does not start
# at all on Ubuntu 22.04, Debian 12 and friends. Built on the oldest supported LTS (the CI job runs on
# ubuntu-22.04), the binary runs everywhere the PCPanel executable itself does.
#
# Pinned to a crate version + sha256 and built with the crate's own Cargo.lock (--locked), for
# reproducible, verifiable builds. To update, bump KDOTOOL_VERSION and KDOTOOL_CRATE_SHA256 together
# (the sha256 of https://static.crates.io/crates/kdotool/kdotool-<version>.crate).
#
# Needs cargo (Rust >= 1.85, the crate is edition 2024), pkg-config and the libdbus-1 headers
# (Debian/Ubuntu: libdbus-1-dev).
#
# Usage:
#   packaging/linux/build-kdotool.sh <dest-dir>
# Installs <dest-dir>/kdotool (executable) and <dest-dir>/kdotool-LICENSE (Apache-2.0 text).
#
# Caching: the built binary + license are cached under ${KDOTOOL_CACHE_DIR:-$HOME/.cache/pcpanel-kdotool}
# per version and reused on later runs, so it is compiled once until the pin changes. In CI, wrap that
# directory with actions/cache keyed on this script's hash (and the runner image, since the binary
# inherits the build host's glibc) so the build happens once per pin.
set -euo pipefail

KDOTOOL_VERSION="0.2.3"
KDOTOOL_CRATE_SHA256="f2eee83d474f719244c71d8ebbe360b5634ed76e4da6bdfae9524cf9de6b9e4c"

DEST_DIR="${1:?usage: build-kdotool.sh <dest-dir>}"

cache_dir="${KDOTOOL_CACHE_DIR:-$HOME/.cache/pcpanel-kdotool}/${KDOTOOL_VERSION}"

if [ -x "$cache_dir/kdotool" ] && [ -f "$cache_dir/LICENSE" ]; then
    echo ">> kdotool ${KDOTOOL_VERSION}: using cached build"
else
    crate="kdotool-${KDOTOOL_VERSION}.crate"
    url="https://static.crates.io/crates/kdotool/${crate}"
    workdir="$(mktemp -d)"
    trap 'rm -rf "$workdir"' EXIT

    echo ">> kdotool ${KDOTOOL_VERSION}: downloading $url"
    curl -fsSL --retry 3 -o "$workdir/$crate" "$url"
    if ! echo "${KDOTOOL_CRATE_SHA256}  $workdir/$crate" | sha256sum -c - >/dev/null 2>&1; then
        echo "error: sha256 mismatch for ${crate} (expected ${KDOTOOL_CRATE_SHA256})" >&2
        exit 1
    fi
    tar -xzf "$workdir/$crate" -C "$workdir"

    echo ">> kdotool ${KDOTOOL_VERSION}: building"
    src="$workdir/kdotool-${KDOTOOL_VERSION}"
    cargo build --release --locked --manifest-path "$src/Cargo.toml" --target-dir "$workdir/target"

    mkdir -p "$cache_dir"
    install -m 0755 "$workdir/target/release/kdotool" "$cache_dir/kdotool"
    install -m 0644 "$src/LICENSE" "$cache_dir/LICENSE"
fi

mkdir -p "$DEST_DIR"
install -m 0755 "$cache_dir/kdotool" "$DEST_DIR/kdotool"
install -m 0644 "$cache_dir/LICENSE" "$DEST_DIR/kdotool-LICENSE"
echo ">> kdotool ${KDOTOOL_VERSION} installed to ${DEST_DIR}/kdotool"

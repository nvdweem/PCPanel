#!/usr/bin/env bash
# Build an AppImage for the PCPanel Quarkus native image.
#
# AppImage is the recommended download for immutable distros (Fedora Silverblue/
# Kinoite, Bazzite, openSUSE Aeon/MicroOS, SteamOS, ...): a single self-contained
# file that runs without installing anything into the OS and without a sandbox, so
# raw USB HID access and host tools (pactl, xdotool/kdotool) work natively.
#
# The native image is not a single file: native-image emits the executable plus
# companion shared libraries (libawt_xawt.so, libfontmanager.so, ...) that the
# binary loads from its own directory. They are placed next to the executable in
# the AppDir under usr/bin.
#
# Usage:
#   packaging/linux/appimage/build-appimage.sh <version> <native-exe> <output-dir>
#
# Environment:
#   APPIMAGETOOL  - path to appimagetool (default: "appimagetool" on PATH)
#   ARCH          - target arch for appimagetool (default: x86_64)
set -euo pipefail

VERSION="${1:?usage: build-appimage.sh <version> <native-exe> <output-dir>}"
NATIVE_EXE="${2:?usage: build-appimage.sh <version> <native-exe> <output-dir>}"
OUTPUT_DIR="${3:?usage: build-appimage.sh <version> <native-exe> <output-dir>}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PKG_LINUX="$REPO_ROOT/packaging/linux"
EXE_DIR="$(cd "$(dirname "$NATIVE_EXE")" && pwd)"
APPIMAGETOOL="${APPIMAGETOOL:-appimagetool}"
ARCH="${ARCH:-x86_64}"
export ARCH

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
APPDIR="$WORK/PCPanel.AppDir"

echo ">> Staging AppDir for pcpanel $VERSION ($ARCH)"

install -d "$APPDIR/usr/bin"
install -d "$APPDIR/usr/share/applications"
install -d "$APPDIR/usr/share/metainfo"
install -d "$APPDIR/usr/share/icons/hicolor/256x256/apps"

# Executable + every companion shared library native-image placed next to it.
install -m 0755 "$NATIVE_EXE" "$APPDIR/usr/bin/PCPanel"
shopt -s nullglob
for lib in "$EXE_DIR"/*.so "$EXE_DIR"/*.so.*; do
    install -m 0644 "$lib" "$APPDIR/usr/bin/"
done
shopt -u nullglob

# Bundle kdotool (Apache-2.0) next to the executable so "focus volume" works out of the box on KDE
# Plasma (Wayland and X11) - the recommended AppImage runs unsandboxed, so the bundled kdotool reaches
# the host KWin directly. LinuxProcessHelper prefers a kdotool sibling of its own binary over PATH.
# No-op on non-x86_64 (no upstream prebuilt binary).
bash "$PKG_LINUX/fetch-kdotool.sh" "$APPDIR/usr/bin" || \
    echo ">> WARNING: could not bundle kdotool; focus volume will need a system kdotool/xdotool" >&2

# AppRun launcher.
install -m 0755 "$PKG_LINUX/appimage/AppRun" "$APPDIR/AppRun"

# AppImage requires the .desktop and icon at the AppDir root; also ship them in the
# standard locations for when the AppImage is integrated by a file manager / appimaged.
install -m 0644 "$PKG_LINUX/com.getpcpanel.PCPanel.desktop"     "$APPDIR/com.getpcpanel.PCPanel.desktop"
install -m 0644 "$PKG_LINUX/com.getpcpanel.PCPanel.desktop"     "$APPDIR/usr/share/applications/com.getpcpanel.PCPanel.desktop"
install -m 0644 "$PKG_LINUX/com.getpcpanel.PCPanel.metainfo.xml" "$APPDIR/usr/share/metainfo/com.getpcpanel.PCPanel.metainfo.xml"
install -m 0644 "$REPO_ROOT/app-icon.png" "$APPDIR/com.getpcpanel.PCPanel.png"
install -m 0644 "$REPO_ROOT/app-icon.png" "$APPDIR/usr/share/icons/hicolor/256x256/apps/com.getpcpanel.PCPanel.png"
cp "$APPDIR/com.getpcpanel.PCPanel.png" "$APPDIR/.DirIcon"

mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
OUT="$OUTPUT_DIR/PCPanel-${VERSION}-${ARCH}.AppImage"

echo ">> Building AppImage with $APPIMAGETOOL"
"$APPIMAGETOOL" "$APPDIR" "$OUT"
echo ">> Built $OUT"

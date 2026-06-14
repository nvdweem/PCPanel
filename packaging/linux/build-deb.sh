#!/usr/bin/env bash
# Build a Debian package for the PCPanel Quarkus native image.
#
# The GraalVM native image is not a single self-contained file: native-image emits
# the executable plus companion shared libraries (libawt_xawt.so, libfontmanager.so,
# libjvm.so, liblcms.so, ...) that the binary loads from its own directory at runtime
# via java.library.path. They are installed together under /opt/pcpanel.
#
# Usage:
#   packaging/linux/build-deb.sh <version> <native-exe> <output-dir>
# Example:
#   packaging/linux/build-deb.sh 1.8.123 target/pcpanel-1.8-SNAPSHOT-runner target
set -euo pipefail

VERSION="${1:?usage: build-deb.sh <version> <native-exe> <output-dir>}"
NATIVE_EXE="${2:?usage: build-deb.sh <version> <native-exe> <output-dir>}"
OUTPUT_DIR="${3:?usage: build-deb.sh <version> <native-exe> <output-dir>}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PKG_LINUX="$REPO_ROOT/packaging/linux"
EXE_DIR="$(cd "$(dirname "$NATIVE_EXE")" && pwd)"

ARCH="$(dpkg --print-architecture)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
chmod 0755 "$STAGE"   # mktemp creates 0700; the package root (/) must not be 0700

echo ">> Staging .deb tree for pcpanel $VERSION ($ARCH)"

install -d "$STAGE/opt/pcpanel"
install -d "$STAGE/usr/bin"
install -d "$STAGE/usr/share/applications"
install -d "$STAGE/usr/share/metainfo"
install -d "$STAGE/usr/share/icons/hicolor/256x256/apps"
install -d "$STAGE/usr/lib/udev/rules.d"
install -d "$STAGE/DEBIAN"

# Executable + every companion shared library native-image placed next to it.
install -m 0755 "$NATIVE_EXE" "$STAGE/opt/pcpanel/PCPanel"
shopt -s nullglob
for lib in "$EXE_DIR"/*.so "$EXE_DIR"/*.so.*; do
    install -m 0644 "$lib" "$STAGE/opt/pcpanel/"
done
shopt -u nullglob

# Launcher on PATH.
ln -s /opt/pcpanel/PCPanel "$STAGE/usr/bin/pcpanel"

# Desktop integration.
install -m 0644 "$PKG_LINUX/com.getpcpanel.PCPanel.desktop"  "$STAGE/usr/share/applications/"
install -m 0644 "$PKG_LINUX/com.getpcpanel.PCPanel.metainfo.xml" "$STAGE/usr/share/metainfo/"
install -m 0644 "$REPO_ROOT/app-icon.png" "$STAGE/usr/share/icons/hicolor/256x256/apps/com.getpcpanel.PCPanel.png"
install -m 0644 "$PKG_LINUX/70-pcpanel.rules" "$STAGE/usr/lib/udev/rules.d/70-pcpanel.rules"

INSTALLED_SIZE="$(du -ks "$STAGE/opt" | cut -f1)"

cat > "$STAGE/DEBIAN/control" <<EOF
Package: pcpanel
Version: $VERSION
Section: sound
Priority: optional
Architecture: $ARCH
Maintainer: nvdweem <https://github.com/nvdweem/PCPanel>
Installed-Size: $INSTALLED_SIZE
Depends: libc6, libfreetype6, libfontconfig1, zlib1g, libx11-6, libxext6, libxrender1, libxtst6, libxi6, libusb-1.0-0
Recommends: pulseaudio-utils, xdotool
Provides: pcpanel
Homepage: https://github.com/nvdweem/PCPanel
Description: Control software for PCPanel devices
 Third-party / community controller software for PCPanel devices. Map dials and
 sliders to volume, focus volume, WaveLink, OBS, MQTT, OSC and more.
 .
 This software is not affiliated with PCPanel Software.
EOF

cat > "$STAGE/DEBIAN/postinst" <<'EOF'
#!/bin/sh
set -e
case "$1" in
    configure)
        # Apply the udev rules so device access works without a reboot.
        if command -v udevadm >/dev/null 2>&1; then
            udevadm control --reload-rules || true
            udevadm trigger || true
        fi
        if command -v update-desktop-database >/dev/null 2>&1; then
            update-desktop-database -q /usr/share/applications || true
        fi
        if command -v gtk-update-icon-cache >/dev/null 2>&1; then
            gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true
        fi
        ;;
esac
exit 0
EOF
chmod 0755 "$STAGE/DEBIAN/postinst"

cat > "$STAGE/DEBIAN/postrm" <<'EOF'
#!/bin/sh
set -e
case "$1" in
    remove|purge)
        if command -v update-desktop-database >/dev/null 2>&1; then
            update-desktop-database -q /usr/share/applications || true
        fi
        ;;
esac
exit 0
EOF
chmod 0755 "$STAGE/DEBIAN/postrm"

mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
DEB_PATH="$OUTPUT_DIR/pcpanel_${VERSION}_${ARCH}.deb"

# Build reproducibly enough; root-owned tree is faked so files don't carry the builder's uid.
dpkg-deb --root-owner-group --build "$STAGE" "$DEB_PATH"
echo ">> Built $DEB_PATH"

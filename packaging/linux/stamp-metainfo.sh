#!/usr/bin/env bash
# Stamp the built version into the AppStream metainfo at package time.
#
# The <release> entry is pure derived data — a version and a date, no notes — so committing it only
# created a second file that every release had to edit, and therefore a second file that conflicted
# on every forward merge of a maintenance branch into main. The release version now lives solely in
# the git tag (see .github/workflows/build-and-release.yml); this stamps it into the copy that the
# .deb / AppImage / Flatpak each install to /usr/share/metainfo.
#
# The committed file keeps a placeholder entry so `appstreamcli validate` and local packaging still
# work on a plain checkout.
#
# Usage: packaging/linux/stamp-metainfo.sh <version> [date]
set -euo pipefail

VERSION="${1:-}"
DATE="${2:-$(date +%F)}"
if [ -z "$VERSION" ]; then
    echo "usage: packaging/linux/stamp-metainfo.sh <version> [YYYY-MM-DD]" >&2
    exit 2
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
METAINFO="$REPO_ROOT/packaging/linux/com.getpcpanel.PCPanel.metainfo.xml"

if [ ! -f "$METAINFO" ]; then
    echo "error: $METAINFO not found" >&2
    exit 1
fi

# Portable in-place edit: GNU and BSD sed disagree on -i, so write via a temp file.
sed "s|<release version=\"[^\"]*\" date=\"[^\"]*\" */>|<release version=\"$VERSION\" date=\"$DATE\" />|" \
    "$METAINFO" > "$METAINFO.tmp" && mv "$METAINFO.tmp" "$METAINFO"

echo "metainfo <release>: version=$VERSION date=$DATE"
grep -F "<release " "$METAINFO"

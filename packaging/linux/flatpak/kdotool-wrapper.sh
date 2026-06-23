#!/bin/sh
# Run the Flatpak-bundled kdotool with a temp dir that the host KWin can read.
#
# kdotool writes its generated KWin script to a temp file and passes that path to
# org.kde.KWin.loadScript over D-Bus. KWin runs on the host, so it must be able to *read that path*.
# The Flatpak sandbox's private /tmp is not visible to the host, so kdotool's default temp location
# would make loadScript fail (window lookup silently returns nothing - the #88 symptom).
#
# The app's persistent data dir (~/.var/app/com.getpcpanel.PCPanel) is exposed unchanged as $HOME
# inside the sandbox and is a real path under the user's home that the user's KWin can read. Point
# kdotool's TMPDIR there so the script file lands on a host-visible path and loadScript succeeds.
#
# Requires the manifest to grant --talk-name=org.kde.KWin.
dir="${XDG_CACHE_HOME:-$HOME/.cache}/pcpanel/kdotool-tmp"
mkdir -p "$dir" 2>/dev/null || true
exec env TMPDIR="$dir" /app/pcpanel/kdotool "$@"

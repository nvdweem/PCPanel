#!/bin/sh
# Run the Flatpak-bundled kdotool with a temp dir that the host KWin can read.
#
# kdotool writes its generated KWin script to a temp file and passes that path to
# org.kde.KWin.loadScript over D-Bus. KWin runs on the host, so it must be able to *read that path*.
# The Flatpak sandbox's private /tmp is not visible to the host, so kdotool's default temp location
# would make loadScript fail (window lookup silently returns nothing - the #88 symptom).
#
# The dirs that ARE shared with the host (same backing store, SAME absolute path inside and out) are
# the per-app XDG dirs under ~/.var/app/<id>: XDG_CACHE_HOME resolves to
# /home/<user>/.var/app/<id>/cache both inside the sandbox and on the host, so a file written there is
# readable by the host KWin at the identical path. NOTE: $HOME inside the sandbox is /home/<user> - an
# unbacked sandbox overlay, NOT the per-app dir - so $HOME/.cache must NOT be used here (the host can't
# see it). Point kdotool's TMPDIR at XDG_CACHE_HOME (verified host-visible against a sandboxed-home
# Flatpak). The fallback derives the same per-app cache path from $FLATPAK_ID for the rare case the env
# var is unset; both resolve to a host-readable path.
#
# Requires the manifest to grant --talk-name=org.kde.KWin.
cache="${XDG_CACHE_HOME:-$HOME/.var/app/$FLATPAK_ID/cache}"
dir="$cache/pcpanel/kdotool-tmp"
mkdir -p "$dir" 2>/dev/null || true
exec env TMPDIR="$dir" /app/pcpanel/kdotool "$@"

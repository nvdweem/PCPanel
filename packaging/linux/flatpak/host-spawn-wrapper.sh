#!/bin/sh
# Generic "run this command on the host" shim. PCPanel shells out to host tools
# (pactl for PulseAudio/PipeWire volume control, xdotool/kdotool for the focused
# window). Those binaries do not exist inside the Flatpak sandbox, so we forward
# the call to the host via flatpak-spawn. The wrapper is installed under
# /app/bin/{pactl,xdotool,kdotool} so the app's bare-name invocations resolve here.
#
# Requires the manifest to grant --talk-name=org.freedesktop.Flatpak.
exec flatpak-spawn --host "$(basename "$0")" "$@"

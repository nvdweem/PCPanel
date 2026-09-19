#!/bin/sh
# Generic "run this command on the host" shim. PCPanel shells out to host tools
# (pactl for PulseAudio/PipeWire volume control, xdotool/kdotool/hyprctl for the
# focused window). Those binaries do not exist inside the Flatpak sandbox, so we
# forward the call to the host via flatpak-spawn. The wrapper is installed under
# /app/bin/{pactl,xdotool,hyprctl} so the app's bare-name invocations resolve here
# (kdotool has its own wrapper).
#
# Requires the manifest to grant --talk-name=org.freedesktop.Flatpak.
exec flatpak-spawn --host "$(basename "$0")" "$@"

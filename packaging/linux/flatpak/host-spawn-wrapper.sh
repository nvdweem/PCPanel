#!/bin/sh
# Generic "run this command on the host" shim. PCPanel shells out to host tools
# (pactl for PulseAudio/PipeWire volume control, xdotool for the focused window).
# Those binaries do not exist inside the Flatpak sandbox, so we forward the call
# to the host via flatpak-spawn. The wrapper is installed under
# /app/bin/{pactl,xdotool} so the app's bare-name invocations resolve here.
#
# It suits only tools that need nothing from the session environment, because
# `flatpak-spawn --host` runs the host command with flatpak-session-helper's
# environment rather than the sandbox's. kdotool and hyprctl each need something
# carried across, so both have their own wrapper.
#
# Requires the manifest to grant --talk-name=org.freedesktop.Flatpak.
exec flatpak-spawn --host "$(basename "$0")" "$@"

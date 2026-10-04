#!/bin/sh
# Runs the host's parec for the audio-level lights (it is not in the Flatpak runtime). Like
# host-spawn-wrapper.sh, but --watch-bus ends the host recording when PCPanel's bus connection goes
# away, so a crashed or killed PCPanel leaves no parec running on the host. Stopping a recording
# normally (SIGTERM to this process) is forwarded to the host parec by flatpak-spawn.
#
# Requires the manifest to grant --talk-name=org.freedesktop.Flatpak.
exec flatpak-spawn --host --watch-bus parec "$@"

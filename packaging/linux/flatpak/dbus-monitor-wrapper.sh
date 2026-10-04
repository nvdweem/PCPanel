#!/bin/sh
# Runs the host's dbus-monitor for the notification lights: the sandbox's D-Bus proxy does not let a
# monitor see other apps' messages. Like parec-wrapper.sh, --watch-bus ends the host dbus-monitor when
# PCPanel's bus connection goes away, so a crashed or killed PCPanel leaves none running on the host;
# stopping it normally (SIGTERM to this process) is forwarded to the host process by flatpak-spawn.
#
# Requires the manifest to grant --talk-name=org.freedesktop.Flatpak.
exec flatpak-spawn --host --watch-bus dbus-monitor "$@"

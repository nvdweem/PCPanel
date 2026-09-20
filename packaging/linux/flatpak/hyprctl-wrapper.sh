#!/bin/sh
# Run the host's hyprctl with the Hyprland instance signature it needs.
#
# hyprctl finds the compositor's IPC socket at
# $XDG_RUNTIME_DIR/hypr/$HYPRLAND_INSTANCE_SIGNATURE/.socket.sock, and refuses to do anything at all
# when that variable is unset ("HYPRLAND_INSTANCE_SIGNATURE not set! (is hyprland running?)", exit 1).
# It does not fall back to scanning the socket directory, not even when exactly one instance is there.
#
# The generic host-spawn shim cannot satisfy that, because `flatpak-spawn --host` does not carry the
# sandbox's environment over: the host command inherits the environment of
# flatpak-session-helper.service, which has HOME/PATH/XDG_RUNTIME_DIR/DBUS_SESSION_BUS_ADDRESS but
# nothing from the Hyprland session. So the signature has to be handed across explicitly with --env.
#
# It is taken from our own environment when the session provided it, and otherwise recovered from the
# socket directory, which --filesystem=xdg-run/hypr:ro binds in. That fallback is what covers a
# D-Bus/systemd-activated launch, where the variable reaches neither us nor the host command (Hyprland
# does not run dbus-update-activation-environment by default). Newest directory wins, so an instance
# dir left behind by a crashed session loses to the live one.
#
# Requires the manifest to grant --talk-name=org.freedesktop.Flatpak and --filesystem=xdg-run/hypr:ro.
sig="$HYPRLAND_INSTANCE_SIGNATURE"
if [ -z "$sig" ]; then
  sig=$(ls -t "${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/hypr" 2>/dev/null | head -1)
fi
exec flatpak-spawn --host --env=HYPRLAND_INSTANCE_SIGNATURE="$sig" hyprctl "$@"

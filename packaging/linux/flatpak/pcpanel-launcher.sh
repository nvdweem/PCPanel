#!/bin/sh
# Flatpak entry point. The native image loads its companion shared libraries
# (libawt_xawt.so, libfontmanager.so, ...) from its own directory, so we exec the
# binary in place rather than from a different working directory.
exec /app/pcpanel/PCPanel "$@"

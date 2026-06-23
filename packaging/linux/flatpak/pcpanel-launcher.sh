#!/bin/sh
# Flatpak entry point. The native image loads its companion shared libraries
# (libawt_xawt.so, libfontmanager.so, ...) from its own directory, so we exec the
# binary in place rather than from a different working directory.
#
# Point the kdotool command at the wrapper (which sets a host-visible TMPDIR so KWin can read the
# script kdotool generates). Without this override LinuxProcessHelper.bundledSibling would resolve the
# raw /app/pcpanel/kdotool sitting next to the executable and bypass the wrapper, leaving kdotool's
# script in the sandbox-private /tmp where the host KWin can't read it (#88 symptom). An explicit path
# (contains '/') is honoured verbatim, so this beats the bundled-sibling lookup. LINUX_COMMANDS_KDOTOOL
# maps to the `linux.commands.kdotool` config property.
exec env LINUX_COMMANDS_KDOTOOL=/app/bin/kdotool /app/pcpanel/PCPanel "$@"

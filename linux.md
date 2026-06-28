# Linux installation

Note: The software is not actively being developed for Linux, but attempts are being made to
stay/become more Linux compatible. If there are any issues please report them via an issue.

Due to there being a lot of Linux distributions there are bound to be features that won't work.
Focus volume on KDE Plasma (both Wayland and X11) needs `kdotool`, which is now **bundled** with the
`.deb`, AppImage and Flatpak — so it works out of the box without installing anything extra. If you run
the raw executable, or focus volume can't find a window tool, the app logs a clear warning (and shows a
desktop notification) pointing you at `kdotool`.

## Preparation

The `.deb` package installs the udev rules (`/usr/lib/udev/rules.d/70-pcpanel.rules`) and reloads them automatically, so when
installing the Debian package the steps below are handled for you. **Every other install — AppImage, the raw executable, and
the Flatpak — needs you to add the udev rule yourself.** This includes the Flatpak: `--device=all` lets the sandbox *see* the
device node, but the node is still owned `root:root` with mode `0600` on the host, and only a `uaccess` udev rule grants your
logged-in user permission to open it. Without it the log repeats `Unable to open device … will keep retrying` and the UI shows
the "No PCPanel connected" empty state even though the device was detected. Set the access up yourself:

1. Allow the software to access the device:
   ```shell
   sudoedit /etc/udev/rules.d/70-pcpanel.rules
   ```
1. Add the following lines:
   ```properties
   KERNEL=="hidraw*", SUBSYSTEM=="hidraw", ATTRS{idVendor}=="04d8", ATTRS{idProduct}=="eb52", TAG+="uaccess"
   KERNEL=="hidraw*", SUBSYSTEM=="hidraw", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="a3c4", TAG+="uaccess"
   KERNEL=="hidraw*", SUBSYSTEM=="hidraw", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="a3c5", TAG+="uaccess"
   ```
   These grant your user access to the device's hidraw node (`/dev/hidrawN`). Without them the device is
   detected but never opens (`Unable to open device …` in the log).
1. Then run
   ```shell
   sudo udevadm control --reload-rules && sudo udevadm trigger
   ```
   (`udevadm trigger` applies the rule to the already-connected device, so a replug is not needed.)
1. (Optional) Make the software startup automatically. When making the application startup automatically you can add the `quiet` parameter to not show the main window on startup.

If it still doesn't work, try restarting your computer (logging out is not enough).

The software depends on:

- `libusb-1.0-0` (provides `libusb-1.0.so.0`) — required for USB/HID device access. Without it the
  app crashes during HID init with `libusb-1.0.so.0: cannot open shared object file`. The `.deb`
  declares it under `Depends:` so it is installed automatically; for a manual/AppImage install make
  sure it is present (`apt-get install libusb-1.0-0`). It is part of the Flatpak runtime already.
- `pulseaudio-utils` (the `pactl` command) — for volume control.
- `kdotool` — to get the currently active window for focus volume on KDE Plasma (Wayland **and** X11).
  This is **bundled** with the `.deb`, AppImage and Flatpak, so you normally don't install it yourself.
  `kdotool` covers X11 too, so `xdotool` is not needed alongside it; `xdotool` only helps on non-KDE X11
  desktops (GNOME/XFCE on X11) and is purely optional.

If there are no tray extensions available, the application will still hide when closed. To show
the main window, just run the application again.

## Install

The application is shipped as a GraalVM native executable, so no Java installation is needed. The build is a 64-bit (`amd64`)
binary that loads its companion shared libraries from its own directory; keep the installed files together.

### Debian / Ubuntu (.deb)

Download the `.deb` file and install with your package manager or via terminal:

   ```shell
   sudo apt install ./pcpanel_[version]_amd64.deb   # resolves dependencies automatically
   # or:
   sudo dpkg -i pcpanel_[version]_amd64.deb
   sudo apt-get -f install   # only needed if dependencies were missing
   ```

This installs the executable to `/opt/pcpanel`, adds a `pcpanel` command on your `PATH`, a desktop entry, and the udev rules.
`pulseaudio-utils` (`pactl`) is recommended for volume control; `kdotool` (for focus volume) is **bundled** next to the
executable, so it works without a system install. `xdotool` is only *suggested* (the non-KDE-X11 fallback).

### Flatpak (best-effort)

A Flatpak bundle is provided as an alternative. Note that the app is primarily developed for Windows and the Flatpak sandbox
makes some features harder; it is provided on a best-effort basis.

   ```shell
   flatpak install --user PCPanel-[version].flatpak
   flatpak run com.getpcpanel.PCPanel
   ```

> **Device access:** the Flatpak still needs the host udev rule from [Preparation](#preparation) above — the sandbox can see
> the device but cannot grant itself permission to open it. If the device is detected but never connects (`Unable to open
> device …` in the log), that rule is missing.

The sandbox is granted USB device access, audio (PulseAudio/PipeWire), network, X11/Wayland and tray permissions. `kdotool`
for focus volume is **bundled inside the sandbox** and talks to the host KWin over D-Bus (`--talk-name=org.kde.KWin`), so KDE
Plasma focus volume works without a host-installed kdotool. Volume control (`pactl`) and the optional non-KDE-X11 fallback
(`xdotool`) are still forwarded to the host via `flatpak-spawn`, so those host tools need to be present for their features.

> **Discord integration:** the sandbox is granted access to Discord's local IPC socket
> (`$XDG_RUNTIME_DIR/discord-ipc-*`, plus the Flatpak/snap Discord locations). Because a Flatpak only
> binds that socket when it already exists at launch, **start Discord before PCPanel** (or restart
> PCPanel after Discord is up); otherwise the Discord settings page reports that Discord isn't running.

### Other

If neither package works on your distribution, download the raw executable from the release (it sits inside the `.deb`
under `/opt/pcpanel`) together with its `.so` libraries and run `./PCPanel`.

## Settings and data location

All user configuration — profiles (`profiles.json`), logs, and the single-instance lock file — is stored in one data
directory. On Linux this follows the [XDG Base Directory spec](https://specifications.freedesktop.org/basedir-spec/) so it
lands in a writable, persisted place on every kind of system (including sandboxed and immutable ones). The directory is
resolved in this order:

1. **`$PCPANEL_ROOT`** — if set, this exact path is used, on any platform. This is the escape hatch for read-only homes,
   custom locations, or running two installs side by side.
2. **Legacy `~/.pcpanel`** — if this directory already exists it keeps being used, so upgrades from older versions (and
   older Flatpak builds) don't lose their profiles.
3. **`$XDG_CONFIG_HOME/pcpanel`**, or **`~/.config/pcpanel`** when `XDG_CONFIG_HOME` is unset — the location for fresh
   installs.

Where this resolves per package:

| Install            | Default settings directory                                              |
| ------------------ | ----------------------------------------------------------------------- |
| `.deb` / AppImage  | `~/.config/pcpanel` (or your `$XDG_CONFIG_HOME/pcpanel`)                 |
| Flatpak            | `~/.var/app/com.getpcpanel.PCPanel/config/pcpanel` (inside the sandbox) |
| Upgraded install   | `~/.pcpanel` is kept if it already exists                               |

### Immutable distros (Silverblue, Kinoite, Bazzite, MicroOS, SteamOS, …)

These work out of the box: only `/usr` is read-only — your `$HOME` and the XDG directories under it stay writable, so the
AppImage and the Flatpak both persist settings normally. The **AppImage is the recommended download** for these systems: it
needs nothing installed into the OS and runs without a sandbox, so USB HID access works natively, the bundled `kdotool` drives
focus volume out of the box, and only `pactl` (volume control) is expected on the host.

### Flatpak settings persistence

The Flatpak writes to its own sandbox home (`~/.var/app/com.getpcpanel.PCPanel/`), which Flatpak persists across runs and
upgrades, so **no host-filesystem permission is needed** and settings are not lost on update. To **import profiles from a
previous non-Flatpak install**, copy your old `profiles.json` into the sandbox directory:

```shell
mkdir -p ~/.var/app/com.getpcpanel.PCPanel/config/pcpanel
cp ~/.pcpanel/profiles.json ~/.var/app/com.getpcpanel.PCPanel/config/pcpanel/
```

### If settings don't persist out of the box

If your environment puts `$HOME` somewhere non-writable, or `user.home` is unset/wrong (some minimal kiosk or container
setups), point the app at a writable directory explicitly with `PCPANEL_ROOT`:

```shell
# .deb / AppImage / manual run
PCPANEL_ROOT="$HOME/.config/pcpanel" pcpanel

# Flatpak: pass the env var and grant the sandbox access to that path
flatpak override --user --env=PCPANEL_ROOT=/var/data/pcpanel \
  --filesystem=/var/data/pcpanel:create com.getpcpanel.PCPanel
```

For autostart units (see below) add the same variable to the unit's environment (`Environment=PCPANEL_ROOT=…` for systemd,
or an `env` prefix in the `Exec=` line for XDG autostart).

## Autostart on login

There is no installer on Linux, so autostart is set up manually. Add the `quiet` argument so the main window stays
hidden on login. Pick whichever fits your setup:

### XDG autostart (most desktops: GNOME, KDE, XFCE, …)

Create `~/.config/autostart/pcpanel.desktop`:

```ini
[Desktop Entry]
Type=Application
Name=PCPanel
Exec=pcpanel quiet
Icon=com.getpcpanel.PCPanel
X-GNOME-Autostart-enabled=true
```

(If you installed manually instead of via the `.deb`, replace `pcpanel` with the full path to the executable, e.g.
`/opt/pcpanel/PCPanel quiet`. For the Flatpak, use `Exec=flatpak run com.getpcpanel.PCPanel quiet`.)

### systemd user service

Create `~/.config/systemd/user/pcpanel.service`:

```ini
[Unit]
Description=PCPanel
After=graphical-session.target
PartOf=graphical-session.target

[Service]
ExecStart=/usr/bin/pcpanel quiet
Restart=on-failure

[Install]
WantedBy=graphical-session.target
```

Then enable it:

```shell
systemctl --user daemon-reload
systemctl --user enable --now pcpanel.service
```

## Wayland / Sway

System tray icons work on Wayland compositors that support the StatusNotifierItem (SNI) protocol:

- **Sway**: Enable tray in swaybar with `tray_output *` or waybar with tray module

The application auto-detects Wayland via `XDG_SESSION_TYPE` or `WAYLAND_DISPLAY` environment variables.

**Media keys** (play/pause, next, previous, stop) are normally injected as `XF86Audio*` key events
through the X server (native X11 or XWayland), which the desktop forwards to the active player. On a
pure Wayland session with *no* X server reachable, PCPanel instead controls the active player directly
over MPRIS on the session D-Bus (the same mechanism `playerctl` uses), so these keep working. The
`mute` action is a system-volume key with no MPRIS equivalent, so it requires an X server.

## Notes

### Disabling the tray icon

The tray icon can be turned off entirely (both the Wayland StatusNotifierItem and the X11/AWT tray,
which can leave a stray 'JavaEmbeddedFrame'). Either:

- pass `-Ddisable.tray` on the command line (e.g. `pcpanel -Ddisable.tray`, or for the Flatpak
  `flatpak run com.getpcpanel.PCPanel -Ddisable.tray`), or
- set the `PCPANEL_DISABLE_TRAY=1` environment variable (handy for autostart units or launchers that
  don't forward extra arguments to the binary as `-D` properties).

If the tray is disabled, the close button still only hides the application; starting it again shows
the main window.

## Development gotchas

### `quarkus:dev` fails to start: `EMFILE: too many open files` (Watchpack)

When running from source (`./mvnw quarkus:dev`), the Angular dev server's file watcher can fail to
start with a flood of:

```
Watchpack Error (watcher): Error: EMFILE: too many open files, watch '…/node_modules/…'
```

Despite the message, this is usually **not** the per-process file-descriptor limit (`ulimit -n`)
nor the inotify *watch* limit (`fs.inotify.max_user_watches`) — both are typically already high.
The real cap is the per-user inotify **instance** limit, which defaults to `128`:

```shell
cat /proc/sys/fs/inotify/max_user_instances    # often 128
```

A busy desktop (browser, IDE/VS Code, file-manager windows, Spotify, the Plasma shell, …) can hold
100+ inotify instances on its own, leaving too few for webpack/Watchpack, which grabs a burst on
startup. A git worktree makes it worse (a second `node_modules` tree to watch). Raise the limit:

```shell
# Apply now (no reboot, nothing needs restarting):
sudo sysctl fs.inotify.max_user_instances=1024

# Persist across reboots:
echo 'fs.inotify.max_user_instances=1024' | sudo tee /etc/sysctl.d/99-inotify.conf
sudo sysctl --system
```

Then restart `quarkus:dev`. To see what is currently consuming instances:

```shell
for p in $(pgrep -u "$USER"); do
  c=$(find /proc/$p/fd -lname 'anon_inode:inotify' 2>/dev/null | wc -l)
  [ "$c" -gt 0 ] && echo "$c  $(tr '\0' ' ' </proc/$p/cmdline | cut -c1-60)"
done | sort -rn | head
```

## Active window volume

To get the active window, the software uses `kdotool` (KDE Plasma, Wayland **and** X11). It is **bundled** with the `.deb`,
AppImage and Flatpak, so this normally needs no setup. The app looks for a `kdotool` next to its own executable first, then on
your `PATH`. On non-KDE X11 desktops it falls back to `xdotool` if that is installed. If neither is available it logs a clear
warning and shows a desktop notification when you use a focus-volume feature.

If you run from source (or want a newer/own build), you can install `kdotool` yourself. For me, the instructions were:

I also needed to update rust (due to compile errors in `cargo install`), so I included `rustup`.

```shell
# Install kdotool
sudo apt install cargo rustup libdbus-1-dev pkg-config
rustup default stable
cargo install kdotool

# Add cargo bin to path
echo 'export PATH="$HOME/.cargo/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

Not sure if a restart is needed after that. I ended up adding an option to point to the executable by adding this VM argument:
`-Dlinux.commands.kdotool=~/.cargo/bin/kdotool`

### Debugging focus volume ("the knob does nothing")

If focus volume doesn't control an app, turn on debug logging to see exactly how the app matches the
focused window to an audio stream. Set this environment variable before launching (it raises the log
level for the app at runtime — verified to work in the shipped native build):

```shell
# AppImage / raw executable
QUARKUS_LOG_CATEGORY__COM_GETPCPANEL__LEVEL=DEBUG ./PCPanel-*.AppImage

# Flatpak
flatpak run --env=QUARKUS_LOG_CATEGORY__COM_GETPCPANEL__LEVEL=DEBUG com.getpcpanel.PCPanel
```

Then turn a focus-volume dial and look in the log (`<data dir>/logs/logging.log`, see above) for
`Focus volume:` lines. They print the identifiers resolved for the focused window
(`pid / process / flatpakAppId / windowClass / windowName`) and every candidate audio stream with its
match keys (`pid / portalAppId / title / executable`) and whether it matched — which makes it obvious
whether the window resolved to the right app id and which stream (if any) was controlled.

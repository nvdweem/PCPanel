# Linux installation

Note: The software is not actively being developed for Linux, but attempts are being made to
stay/become more Linux compatible. If there are any issues please report them via an issue.

Due to there being a lot of Linux distributions there are bound to be features that won't work.
Wayland focus volume is starting to become possible again, install kdotool for that.

## Preparation

The `.deb` package installs the udev rules (`/usr/lib/udev/rules.d/70-pcpanel.rules`) and reloads them automatically, so when
installing the Debian package the steps below are handled for you. If you run the executable manually (or use the Flatpak),
set the device access up yourself:

1. Allow the software to access the device:
   ```shell
   sudoedit /etc/udev/rules.d/70-pcpanel.rules
   ```
1. Add the following lines:
   ```properties
   SUBSYSTEM=="usb", ATTRS{idVendor}=="04D8", ATTRS{idProduct}=="eb52", TAG+="uaccess"
   SUBSYSTEM=="usb", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="a3c4", TAG+="uaccess"
   SUBSYSTEM=="usb", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="a3c5", TAG+="uaccess"
   ```
1. Then run
   ```shell
   sudo udevadm control --reload-rules
   ```
1. Disconnect and re-connect each of your devices (udev rules are applied whenever a device is connected).
1. (Optional) Make the software startup automatically. When making the application startup automatically you can add the `quiet` parameter to not show the main window on startup.

If it still doesn't work, try restarting your computer (logging out is not enough).

The software depends on some PulseAudio commands (`pactl`) from `pulseaudio-utils` for volume control
and `xdotool` (x11) or `kdotool` (wayland) to get the currently active window for focus volume.

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
`pulseaudio-utils` (`pactl`) and `xdotool` are pulled in as recommended packages for volume control and focus detection.

### Flatpak (best-effort)

A Flatpak bundle is provided as an alternative. Note that the app is primarily developed for Windows and the Flatpak sandbox
makes some features harder; it is provided on a best-effort basis.

   ```shell
   flatpak install --user PCPanel-[version].flatpak
   flatpak run com.getpcpanel.PCPanel
   ```

The sandbox is granted USB device access, audio (PulseAudio/PipeWire), network, X11/Wayland and tray permissions. Volume
control (`pactl`) and focus detection (`xdotool`/`kdotool`) are forwarded to the host via `flatpak-spawn`, so those host tools
still need to be installed on your system.

### Other

If neither package works on your distribution, download the raw executable from the release (it sits inside the `.deb`
under `/opt/pcpanel`) together with its `.so` libraries and run `./PCPanel`.

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

## Notes

### X11 tray issues

In certain cases there will be a 'JavaEmbeddedFrame' when the application is running. This is caused by the tray icon.
It is possible to disable the tray icon and removing the JavaEmbeddedFrame by adding `-Ddisable.tray` to the command line.
If the tray is disabled, the close button will still only hide the application. Starting the application again will show the main window.

## Active window volume

To get the active window, the software uses either `xdotool` (probably available in app-stores) or `kdotool` (which might not).

For me, the installation instructions for `kdotool` were:

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

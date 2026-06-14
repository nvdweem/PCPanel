# macOS installation

Note: macOS support is experimental and best-effort. Development focus is Windows; macOS is community
contributed. Device volume, mute and default-device switching work, but per-application volume is not
possible on stock macOS (Core Audio has no per-application sessions without a virtual audio driver).
Please report issues via the [issue tracker](https://github.com/nvdweem/PCPanel/issues).

The application is shipped as a GraalVM native executable, so no Java installation is needed. The build is
architecture-specific: download the `aarch64` `.dmg` for Apple Silicon (M1 and newer) and the `x86_64`
`.dmg` for Intel Macs. No drivers are needed — the PCPanel is a standard USB HID device.

## Install

1. Download the `PCPanel-<version>-<arch>.dmg` for your architecture and drag `PCPanel.app` to
   `/Applications`.
2. The app is **not signed or notarized**, so macOS Gatekeeper will refuse to open it on first launch.
   Work around it with one of:
   - Right-click `PCPanel.app` → **Open** → **Open** in the dialog, or
   - System Settings → Privacy & Security → scroll down → **Open Anyway**, or
   - From a terminal: `xattr -dr com.apple.quarantine /Applications/PCPanel.app`

## Permissions

macOS guards the APIs PCPanel needs behind privacy (TCC) permissions. Grant them in
**System Settings → Privacy & Security**, then restart PCPanel:

| Permission           | Needed for                                              | Where to enable |
|----------------------|---------------------------------------------------------|-----------------|
| **Input Monitoring** | Reading the PCPanel device (without it the device does not even appear) | Privacy & Security → Input Monitoring |
| **Accessibility**    | Sending keystroke / shortcut mappings                   | Privacy & Security → Accessibility |
| **Automation**       | Controlling Music.app / Spotify from the Media button   | Privacy & Security → Automation (prompted on first use) |

Because the app is unsigned, macOS may forget these grants after an update; re-add PCPanel if a feature
stops working after upgrading. PCPanel logs a hint when it detects a missing permission.

## What works

- Device detection for RGB, Mini and Pro panels (USB HID, cross-platform via hid4java).
- Device volume / mute / default-device switching via Core Audio.
- Per-channel volume fallback for outputs that only expose per-channel controls (e.g. some HDMI /
  DisplayPort monitors).
- Keystroke and shortcut mappings, including the `cmd` modifier.
- Media control of Music.app and Spotify (AppleScript; the player is only controlled if it is already
  running, never launched).
- Run program / end program / profile switching, plus the OBS, OSC, MQTT and Wave Link integrations.

## Limitations

- **Per-application volume / mute / focus volume is not available.** macOS Core Audio has no per-process
  audio sessions, so these commands are no-ops.
- Voicemeeter is Windows-only.
- The Media button's `mute` action has no AppleScript equivalent and is ignored.

## Menu bar / closing the window

Closing the window hides the application to the menu bar (the system tray). Click the menu-bar icon or
launch the app again to bring the window back. Pass the `quiet` argument to start hidden.

## Autostart on login

Add `PCPanel.app` under **System Settings → General → Login Items**. To start hidden, create a
LaunchAgent that passes the `quiet` argument instead. Save the following as
`~/Library/LaunchAgents/com.getpcpanel.PCPanel.plist` and run
`launchctl load ~/Library/LaunchAgents/com.getpcpanel.PCPanel.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.getpcpanel.PCPanel</string>
    <key>ProgramArguments</key>
    <array>
        <string>/Applications/PCPanel.app/Contents/MacOS/PCPanel</string>
        <string>quiet</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>
```

## Building from source

Building the native image requires [GraalVM CE 25](https://www.graalvm.org/) and the Xcode command-line
tools. Builds are single-architecture — build on the architecture you want to ship.

```shell
export GRAALVM_HOME=/path/to/graalvm-ce-25
mvn -B package -Pnative
```

The native executable and its companion `.dylib` files end up under `target/`. The CI workflow
(`.github/workflows/build-and-release.yml`) wraps them into `PCPanel.app` and a `.dmg`; the macOS job is
best-effort and may need tweaking for a given runner image.

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
2. The app is **ad-hoc signed but not notarized** (it has a valid signature, just not one tied to a
   paid Apple Developer ID), so macOS Gatekeeper will still refuse to open it on first launch with a
   "developer cannot be verified" warning. Work around it with one of:
   - From a terminal: `xattr -dr com.apple.quarantine /Applications/PCPanel.app` (then launch), or
   - System Settings → Privacy & Security → scroll down → **Open Anyway** (macOS 15 Sequoia and
     macOS 26 Tahoe require this route — there is a short time window and an admin-password prompt).
   - On older macOS, right-click `PCPanel.app` → **Open** → **Open** in the dialog also worked, but
     Apple removed that shortcut in macOS 15.

   Note: the ad-hoc signature is what makes the quarantine-removal workaround actually function on
   macOS 26 — earlier builds were *unsigned* on Intel and *half-signed* on Apple Silicon (a valid
   binary signature but no sealed bundle manifest), which macOS 26 reports as "PCPanel is damaged
   and can't be opened" and which removing the quarantine flag could not fix (see issue #101).

## Permissions

macOS guards the APIs PCPanel needs behind privacy (TCC) permissions. Grant them in
**System Settings → Privacy & Security**, then restart PCPanel:

| Permission           | Needed for                                              | Where to enable |
|----------------------|---------------------------------------------------------|-----------------|
| **Input Monitoring** | Reading the PCPanel device (without it the device does not even appear) | Privacy & Security → Input Monitoring |
| **Accessibility**    | Sending keystroke / shortcut mappings                   | Privacy & Security → Accessibility |
| **Automation**       | Controlling Music.app / Spotify from the Media button   | Privacy & Security → Automation (prompted on first use) |

Because the app is only ad-hoc signed (its signature is regenerated on every build, so its identity
changes), macOS may forget these grants after an update; re-add PCPanel if a feature stops working
after upgrading. PCPanel logs a hint when it detects a missing permission.

## What works

- Device detection for RGB, Mini and Pro panels (USB HID, cross-platform via hid4java).
- Device volume / mute / default-device switching via Core Audio.
- Per-channel volume fallback for outputs that only expose per-channel controls (e.g. some HDMI /
  DisplayPort monitors).
- Keystroke and shortcut mappings, including the `cmd` modifier (synthesised through CoreGraphics
  `CGEvent`s — works on both Intel and Apple Silicon).
- Media control of Music.app and Spotify (AppleScript; the player is only controlled if it is already
  running, never launched).
- Run program / end program / profile switching, plus the OBS, OSC, MQTT and Wave Link integrations.

## Limitations

- **Per-application volume / mute / focus volume is not available.** macOS Core Audio has no per-process
  audio sessions, so these commands are no-ops.
- Voicemeeter is Windows-only.
- The Media button's `mute` action has no AppleScript equivalent and is ignored.
- **No AWT/Swing-based UI features.** GraalVM/Quarkus cannot bundle AWT (`libawt`) in a macOS native
  image, so anything that relied on it is disabled on macOS: the on-screen volume **overlay**, the
  per-application **icons** in the configuration UI, and the **menu-bar (tray) icon**. These could be
  reimplemented later with native Cocoa/Quartz via JNA (the way the Windows overlay uses a Win32 layered
  window). Audio control and keystrokes are unaffected.

## Closing the window

Closing the browser window only closes the UI; the backend keeps running. Launch `PCPanel.app` again to
reopen the window (a second launch reopens the UI rather than starting a second instance). Pass the
`quiet` argument to start hidden. There is currently no menu-bar icon on macOS (see Limitations).

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

The CI job ad-hoc signs the assembled bundle (`codesign --sign -`) as its final packaging step —
without a valid signature the bundle is reported as "damaged" and will not launch on Apple Silicon
(macOS requires at least an ad-hoc signature to run arm64 code). If you assemble a bundle by hand, do
the same: sign every nested `.dylib` first, then the bundle last (not `--deep`), e.g.
`codesign --force --sign - --timestamp=none PCPanel.app`, and verify with
`codesign --verify --deep --strict PCPanel.app`. No entitlements are needed for an ad-hoc signature.

# PCPanel on macOS

mac support is new so expect some rough edges here and there. if something doesnt work open an issue and ill take a look.

## Install

1. Grab the `.dmg` for your mac from the [releases](https://github.com/nvdweem/PCPanel/releases) page,
   `aarch_64` for Apple Silicon (M1 and newer), `x86_64` for intel macs.
2. Open the dmg and drag PCPanel into Applications.
3. The app isnt signed/notarized right now so the first launch needs one of these:
   - right-click PCPanel.app → Open → Open, or
   - `xattr -dr com.apple.quarantine /Applications/PCPanel.app`, or
   - System Settings → Privacy & Security → "Open Anyway" after it blocks you the first time.

no drivers needed, the PCPanel is just a USB HID device and macOS talks to it directly.

## Permissions you probably need to grant

macOS is picky about apps touching input devices and other apps. depending on what you use you may need to flip these on in System Settings → Privacy & Security:

- **Input Monitoring** - needed to talk to the panel itself. if the app says NO DEVICES CONNECTED but the panel is plugged in and lit up, check this one first (then try a different usb port/cable, that got me once).
- **Accessibility** - only needed if you map Keystroke actions to buttons. without it macOS just eats the fake keypresses and nothing happens, no error.
- **Automation** - first time you use Music Control the system asks if PCPanel can control Music/Spotify. if you click dont allow the media buttons silently do nothing, you can fix it in Privacy & Security → Automation.

heads up: since the app is unsigned, macOS treats every update as a new app, so you may have to re-grant these after updating. annoying but thats apple for you.

## What works

- device detection (RGB, Mini, Pro) over USB, knobs/buttons/sliders, all the lighting stuff
- device volume / mute / default device switching (CoreAudio), outputs and inputs both
- monitors over HDMI/DisplayPort that dont have a master volume - it falls back to setting the channels directly so those mostly work now too
- the volume overlay popup when you turn a knob
- keystroke mappings incl the cmd key, see permissions above
- Music Control buttons - controls Music.app or Spotify directly (theres no system wide media key api apple lets you use, so its per-app. only sends if the player is actually running)
- run/end program, shortcuts, profiles
- OBS, OSC, MQTT and WaveLink integrations

btw the "PCPanel Brightness" knob action changes the brightness of the LEDs on the panel itself, not your monitor. yes i mapped it expecting my screen to dim, no it doesnt do that.

## Limitations

- **per-app volume isnt possible on stock macOS.** Core Audio just has no per-app audio sessions, so "App Volume", "Focus Volume", "Mute App" etc are not available, they show up grayed out so you know why. [Background Music](https://github.com/kyleneideck/BackgroundMusic) works around this with a virtual audio driver, maybe doable natively someday with the process tap stuff from macOS 14.4+ but not today.
- Voicemeeter is a windows program so thats hidden too. if you bring a profile over from windows that uses it, the mapping shows up as "not available" and you can delete it but it wont silently disappear on you.
- sleep/wake isnt detected yet, so the LEDs stay on while the mac sleeps.
- a few weird audio devices genuinely have no volume control at all, dials bound to those wont do anything (you'll see a warning in the log).

## Autostart

System Settings → General → Login Items & Extensions → "Open at Login" → add PCPanel. done.

if you want it to start hidden, the app understands a `quiet` argument but Login Items cant pass arguments, so use a LaunchAgent instead:

```shell
cat > ~/Library/LaunchAgents/com.getpcpanel.pcpanel.plist <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>com.getpcpanel.pcpanel</string>
    <key>ProgramArguments</key>
    <array>
        <string>/Applications/PCPanel.app/Contents/MacOS/PCPanel</string>
        <string>quiet</string>
    </array>
    <key>RunAtLoad</key><true/>
</dict>
</plist>
EOF
launchctl load ~/Library/LaunchAgents/com.getpcpanel.pcpanel.plist
```

## Menu bar

closing the main window hides the app to the menu bar, it keeps running. to get the window back either click the menu bar icon → Show PCPanel, or just click the dock icon.

## Building from source

you need a JDK 25 **that includes JavaFX** (the jlink step bundles the `javafx.*` modules). [Liberica Full JDK](https://bell-sw.com/pages/downloads/) works, make sure its the "full" (jdk-fx) variant:

```shell
export JAVA_HOME=/path/to/liberica-full-25
./mvnw clean install
```

that spits out `target/PCPanel-<version>-<arch>.dmg`. the build is single-arch, build on apple silicon for arm64, on an intel mac for x86_64.

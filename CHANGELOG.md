# PCPanel Controller Software

<!-- Releasenotes without version are included in releases -->

> **⚠️ 2.0 is an early pre-release and is NOT stable.** It may fail to start at all, and you should expect bugs and breaking changes. Do not rely on it. Back up your profiles before upgrading.

- The entire user interface has been rebuilt from the ground up with a brand-new design — a custom dark theme replacing the old windows, covering the device view, action assignment, lighting and settings.
- The app now supports controllers beyond PCPanel through a generalized device layer. PCPanel hardware works exactly as before (and with zero setup), while other devices can be added and bound to the same actions through the same UI.
    - **Deej** — add the open-source Arduino serial volume mixer by its serial port; its sliders map to the same actions as PCPanel dials (no buttons/lights).
    - MIDI controller support is in progress.
- Turn a dial or slider into a multi-position **stepped switch**: split its travel into ranges, give each range its own actions (any number) and its own LED feedback colour. The actions fire the moment you move *into* a range (moving within a range does nothing), and gaps between ranges act as dead-zones. Put a *Switch profile* action on each position to flip between many profiles from a single dial.
- New per-device **base layer**: mark one profile as the fallback used for any control the active profile leaves unconfigured or unlit — actions, lighting and mute colours included. Inherited actions appear on the on-screen device as a dashed chip you can click to edit in place. Combined with a stepped switch on the base layer, a single profile-selector dial keeps working in every profile.
- A **Brightness** dial now controls the global LED brightness as a live runtime value: it wins over each profile's saved brightness and stays put across profile switches, so you configure it once (in any profile) and it governs everywhere. When more than one is configured, the best is chosen automatically.
- The per-control "change colour when muted" option is now an explicit on/off toggle, so black (`#000000`) is a usable muted colour instead of being treated as "off".
- Restored **System Sounds** as a target for the App volume and App mute actions (it had gone missing from the app picker).
- The Device volume and Device mute actions now offer a selectable **Default device** option, so an empty choice is shown and selectable as the default output device.
- Java 25 is now required to run the software. The installer will include it.
- #87 - Experimental macOS support (community contributed by Choaterboater, ported to the new Quarkus build). Device volume/mute/default-device switching via Core Audio, keystrokes, shortcuts and Music/Spotify media control. Per-application volume is not possible on stock macOS. See the [macOS instructions](mac.md).
- Profiles are now saved on application exit, so a change made right before quitting is no longer lost.
- Added support for Elgato Wave Link, enable it in the settings to add the dial/button commands
    - Input devices not yet supported (I don't have one so can't debug)
    - Dials/sliders allow changing volume for Channels, Mixes and Output devices
    - Buttons allow setting mute state, changing the main output, add the focus app to a mix and/or toggle effects
    - **Focus-control Wave Link** (on by default, toggleable on the Wave Link settings page): when an app Wave Link controls has focus, the *Focused-app volume* dial controls that app's Wave Link channel instead of its OS volume — and it switches live the moment you add or remove the app from a channel.
    - Optional **Set volume of controlled apps**: when a Wave-Link-controlled app has focus, pin its OS volume to a configurable percentage (default 100%) so Wave Link does the real mixing — e.g. an app you'd dropped to 50% jumps back up once Wave Link controls it.
- Added support for Home Assistant — control your smart home from your dials and buttons
    - Configure one or more servers (base URL + long-lived access token) on the Home Assistant settings page; actions pick the server automatically when there's only one
    - Actions are pasted as YAML straight from Home Assistant's Developer Tools → Actions page (with a link to open it), so anything Home Assistant can do is available
    - Buttons perform any action; dials map their position into the action — use `{{ value }}` with a min/max range or a translate formula (e.g. a light's brightness or color temperature)
    - Configurable debounce so a moving dial doesn't flood Home Assistant: the first move is sent instantly, then at most one update per the configured interval, and the final value is always sent
- New General setting **Focus volume skips controlled apps** (off by default): when the focused app is already controlled elsewhere — an App-volume action on another control, or a Wave Link channel — the focused-app volume dial leaves it alone instead of fighting that dedicated control.
- **Push-to-talk** — buttons can now run a separate set of actions when *released*, not just when pressed (e.g. unmute on press, mute on release). Configure it under the new "On release" tab when editing a button.
- New generic output actions for dials and buttons, so you can drive almost anything:
    - **HTTP request** (URL, method, headers, body), **MQTT publish** (topic + payload, reusing the MQTT connection) and **OSC send** (address).
    - On a dial the position maps into the message via `{{ value }}` with a min/max range or a translate formula; on a button it sends at full scale — the same value model as the Home Assistant action.
- Expanded **OBS** actions: start/stop/toggle streaming, recording (including pause/resume), virtual camera and replay buffer — alongside the existing scene switch, source mute and source volume.
- The per-control **mute colour** now follows OBS and Wave Link *mute buttons* too, not just their volume dials: a button that mutes an OBS source or a Wave Link channel lights its control in the muted colour while that target is muted.
- Attempts to improve Wayland tray support (now works on Ubuntu)
- #74 - (Linux) New audio sessions should trigger initial volume setting
- #74 - (Linux) New setting 'Force application volume to panel volume', this tries to reset the volume when an application changes it. This seems to solve for instance Firefox from going back to 100% when playing a new song.
- #74 - (Linux) Allow using [kdotool](https://github.com/jinliu/kdotool) for getting the active window on Wayland and control its volume, see linux instructions for more information

## [1.7.1]

- #79 - Added Wayland system tray support via SNI protocol (thanks to @ldumancas)

## [1.7]

**Warning:** This version uses a new format for the savefile.
A backup will be made when converting. If you want to downgrade to a previous version, you can restore the backup.

**New:**

- #38 - Add double click action to buttons
- It is now possible to add more than one action to a button or slider
    - There is also an option to run them all at once when a button is pressed, or sequentially
    - Dials currently only support all at once
- #41 - Dial/Slider values are applied when VoiceMeeter starts
- Dial/Slider values are applied when OBS starts
- #39 - Allow showing the actual volume number (0-100, linear) and additional color settings in overlay
- Dials/sliders can be inverted (combined with multiple commands, this will make it possible to cross-fade, but volume levels aren't useful for that yet)
- The overlay allows showing the actual value (this differs from the slider state when using logarithmic scaling)
- #59 - Additional overlay options (colors, rounding, size and position)
- #45 - Voicemeeter string values are now supported for advanced button actions
- #67 - It's now possible to add a 'dead-zone' (move-start/end) to the dials and sliders which allows better control of multiple items at the same time
- #69 - It's now possible to connect to mqtt. This is a two-way connection for the led's
- #69 - The mqtt option also allows Home Assistant auto-discovery

**Fixes:**

- Pressing escape when a dialog is open will close the dialog
- Version check is done using the GitHub version api and will notify when a new snapshot is available
- [Linux] #44 - pactl commands are run in English so that their output can be parsed correctly
- [Windows] #57 - The end focussed task command works again
- #58 - VoiceMeeter lowest value is now 60 instead of -inf making it behave better

**Fixes within the snapshot:**
(not relevant when not upgrading from a previous snapshot)

- Commands can be removed again
- Commands that are not supported or not enabled are not shown

## [1.6]

**New:**

- [Windows] There is now also an advanced toggle that allows changing the media/communication devices through toggles
- There is now a slider for device brightness. This will apply to all device options. It's also possible to adjust the device brightness via a knob/slider.
- #22 - Mute override colors also work when controlling OBS input volume
- Button action to toggle focus mute ([from request](https://www.reddit.com/r/PCPanel/comments/zyh3sr/toggle_muteunmute_focused_application/))
- Some button actions (the default device toggles) now also show overlay hints ([from request](https://www.reddit.com/r/PCPanel/comments/zhun8a/feature_suggestion_add_a_little_indicatoroverlay/))
- #26 - Allow switching default device for a process (specific or follow focus)
- #27 - Initial support for OSC (Open Sound Control) protocol
- #29 - VoiceMeeter mute state support

**Fixes:**

- If it wasn't possible to get the executable for a path, the AppFinder dialog would not show any results
- When the connection to OBS fails with an error, the reconnect attempts will still be done
- [Windows] Some additional checks are being done in the c++ parts so that the application should crash less (or hopefully not at all anymore)
- [Linux] The application doesn't use `pacmd` anymore, only `pactl` to also be compatible with PipeWire
- [Linux] The application can start when no tray extensions are available
- [Linux] It's possible to disable the tray icon by adding `-Ddisable.tray` as a command line parameter
- [Linux] All audio streams for a process will be changed when a process is controlled
- [Linux] Processes without a PID or Executable can be controlled
- Starting the application again (when skipfilecheck is not specified) will show the main window
- #24 - Controlling Discord via focus volume would also change the microphone volume
- The mute color should not overwrite the color configuration anymore
- The application should start with the profile that was last used (unless a Main profile is specified)

## [1.5]

**New:**

- Added a feature that might fix twitching sliders on faulty hardware (#6)
- [Linux] Allow controlling input volume (#10)
- Allow configuring what the mute override color follows
- OBS Websocket 5 (OBS 28) support. Don't use this version of the software if you are still using OBS 27 or lower without the Websocket 5 plugin.
- [Windows] An option was added to show an overlay when volume is changed and to show icons of the controlled applications in the main ui.
  From [request](https://www.reddit.com/r/PCPanel/comments/xf14ol)

**Fixes:**

- An error would be logged when the mute override service triggered while a device did not use custom colors
- The application names are now case-insensitive for volume changing or muting
- Profile switching using a shortcut is disabled on Linux, it crashed the VM on certain distros
- OBS volume range is now from -97 (-inf db) to 0 instead of -100 to 26
- Removed Roboto font, hoping to fix [this issue](https://www.reddit.com/r/PCPanel/comments/xh0dy4/)

## [1.4]

**New:**

- Added button action to set the default device based on the (partial) name. This might be helpful if Windows reconnects devices with different id's.
- The 'Toggle device' action only works for all output or all input devices, not a combination of both
- Allow controlling only Spotify with the media keys instead of any application that is playing sound (fix for #3)

**Fixes:**

- When connecting a mini or rgb for the first time, an NPE would occur which would prevent the software from finding the device.
- The right-click link to open lighting options was broken, should actually work now.
- Fix the process picker, it gave a ClassCastException
- Allow setting system sounds when the process does not have pid 0
- It's possible to drag audio devices into the Selected Devices list in the Toggle Device button option again (fix for #2)
- Updated JNativeHook so that dead keys keep working (fix for #4)
- Applications that have multiple audio sessions should now be controlled correctly

## [1.3]

**New:**

- When there is no saved profile but there is one from the original software, ask to migrate on startup
- Right clicking a knob or slider opens the lighting dialog
    - Middle click already triggered the click action (I did not know that)
- It's now possible to configure a 'Mute override' color which will show a different color when the controlled device is muted
- Volume change actions now have an option to unmute the device/process
- The App Finder dialogs have a filter field
- An option was added to assign a shortcut to profiles to switch between them
- **Initial Linux compatibility**
    - A lot of options probably won't work correctly yet
    - The UI seems a bit buggy on Ubuntu 22.04 (flashing/blacking out)
    - But volume controlling seems to work mostly for processes, devices and focus volume

**Fixes:**

- Its possible to configure an alternative profile folder for development purposes
- Controlling OBS volume is now done with [db's instead of mul](https://github.com/obsproject/obs-websocket/blob/4.x-compat/docs/generated/protocol.md#setvolume), this gives the slider a better range

## [1.2]

**New:**

- Added options to automatically switch to a profile when other windows get focus
- You can set a main profile which will be loaded when you start the application
- Added version checker. If a new version is released the UI will show that with a link to the download page.
- More than 2 processes can be selected for volume control
- More than 1 process can be selected for muting
- If a controlled application starts, the volume will be set immediately

**Fixes:**

- Profile save structure is changed to allow adding new features more easily
- Getting the list of running processes doesn't crash the application anymore
- The 'Open logfile' button now opens the correct folder

## [1.1.1]

- Adding/removing devices should now work

## [1.1]

- Added SndCtrl.dll as a replacement for sndctrl.exe
- Put all changing files in the user directory (settings, log-files, etc)
- Removed dependency on MediaKeys.dll
- VoiceMeeter path can be configured
- File pickers start at their current selection
- System Sounds volume can be changed
- Terminate process dialog shows all processes, not only the ones with sound
- Removed javafx.web dependency to make the bundle smaller
- Sleep detection is now done without sndctrl.exe, turns off the lights for all devices and works on lock/unlock too
- List of devices is also done without sndctrl.exe, sndctrl.exe is no longer needed

## [1.0]

- Decompile and cleanup of original app
- Added logging framework
- JPackage installer
- Make PCPanel start after install
- Add autostart registry entry and allow not installing shortcuts
- Store saved state in user profile
- Changed title and version number

# PCPanel Controller Software

<!-- Releasenotes without version are included in releases -->

- Java 25 is now required to run the software. The installer will include it.
- Added support for Elgato Wave Link, enable it in the settings to add the dial/button commands
    - Input devices not yet supported (I don't have one so can't debug)
    - Dials/sliders allow changing volume for Channels, Mixes and Output devices
    - Buttons allow setting mute state, changing the main output, add the focus app to a mix and/or toggle effects
- Attempts to improve Wayland tray support (now works on Ubuntu)
- #74 - (Linux) New audio sessions should trigger initial volume setting
- #74 - (Linux) New setting 'Force application volume to panel volume', this tries to reset the volume when an application changes it. This seems to solve for instance Firefox from going back to 100% when playing a new song.

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

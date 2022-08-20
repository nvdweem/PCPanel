# PCPanel Controller Software

<!-- Releasenotes without version are included in releases -->

**New:**

- Added a feature that might fix twitching sliders on faulty hardware (#6)

**Fixes:**

- An error would be logged when the mute override service triggered while a device did not use custom colors
- The application names are now case-insensitive for volume changing or muting
- Profile switching using a shortcut is disabled on Linux, it crashed the VM on certain distros

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

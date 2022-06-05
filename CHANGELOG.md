# PCPanel Controller Software

<!-- Releasenotes without version are included in releases -->

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

# PCPanel Controller Software

<!-- Releasenotes without version are included in releases -->

- New **Report a problem** option, on the bug button next to the settings icon and in the tray menu. It asks what went wrong, how to reproduce it and what you expected, then saves a zip with the logs and details next to your settings and opens both the already filled-in GitHub issue and the folder holding the zip, so all you do is attach it. Passwords and tokens are removed from anything it collects, and you choose what goes in: the log and system details are included by default, your configuration only if you tick it. Open the zip and check it before attaching, especially if you use OBS, MQTT, Home Assistant or Discord.
- When something goes wrong, the error message itself now offers **Report this**, which starts that report with the error already written down and the failure details attached.
- PCPanel now records more about failed requests in its log, so a problem you report can be diagnosed from the log instead of guessed at. Requests are logged to a separate `access.log` next to the existing log file.

- #145 - Fixed the panel lights staying off after booting the PC, until any lighting setting was toggled. Windows lock detection was *inferred* from an internal desktop check that also fails while the desktop is still being set up right after logon (and during a UAC prompt, or a desktop switch), so a false "workstation locked" switched the panels off with nothing left to switch them back on. Lock and unlock now come from Windows itself.
- As a result the panels also switch off **when you lock the PC**, instead of only once you click through to the password/PIN prompt, and come back on when you sign in.
- (Windows) Restored monitor-off/on and going-to-sleep detection, which had never actually started: the hidden window that receives those notifications failed to be created on every launch, so the panels only dimmed on lock. They now switch off when the monitors go to sleep and when Windows suspends, and light up again on wake.
- #145 - Fixed the panel lights coming on after **booting the PC** but switching off again a few seconds later, staying off until a lighting setting was changed. The previous fix helped when restarting the app, but right after a boot Windows itself isn't fully up yet, and the app could mistake that for the PC being locked — and switch the lights off. Locking and unlocking still turn the lights off and back on as intended.
- New setting **"Lights off when locked or asleep"** (General, on by default): controls whether the panel lights switch off when you lock the PC, the monitors go to sleep or the PC suspends. Turn it off if your lights ever switch off when they shouldn't — the app then leaves the lighting alone entirely (the lights still turn off when the app closes).
- Launching PCPanel while it is already running no longer switches the panel lights off. The second launch used to start up far enough to take over the panel and then, as it exited, turn the lights off on the already-running app — leaving them dark until you next changed a lighting setting. It now recognises the running app and steps aside immediately, opening the interface instead of touching the panel.
- **The local web interface is now access-controlled**, so only PCPanel itself can open it and other programs on your PC can't reach it. Open the interface from the tray icon (right-click the PCPanel tray icon → *Open PCPanel*). If you ever land on a **lock screen**, it simply means the page was opened or reloaded outside the app — an old tab or bookmark, or a reload after the app restarted. To unlock it, reopen the interface from the tray icon (or use the button on the lock screen itself).
- New **Open web UI** button action: assign it to a PCPanel button to open the interface in your browser whenever you like — another quick way back in if you hit the lock screen.
- New tray menu item **Copy UI link**: copies a one-time link to the interface onto your clipboard, ready to paste into any browser — handy if the interface didn't open on its own.
- New **Set clipboard** button action: copies a piece of text you choose onto the clipboard when the button is pressed.
- Saved integration secrets (OBS, MQTT, Home Assistant and Discord passwords and tokens) are no longer sent back to the browser: each shows as already configured, and you only enter a value when you want to change it.
- **Security fix:** closed a hole in the web interface's access control (2.0.86). Another program on your PC could still reach the interface and change your settings — or shut PCPanel down — by dressing up the web address in a way the access check did not recognise but the app still accepted. Websites you visit were never able to read your settings this way. Updating is enough; nothing to change on your side.
- Fixed crashes in the Windows audio layer. Plugging or unplugging an audio device, or an app releasing its audio session, could crash PCPanel — the handlers that react to those changes could run while the objects they belonged to were being destroyed. A device reporting an unexpected value for its name is no longer read as if it were text. Also fixed a crash while shutting down with the focused-app watcher still running.
- Fixed the **App volume** action doing nothing when it was aimed at the default output device (it logged an error instead), including on a PC with no default playback device.
- (Linux) **AppImage and Flatpak installs that follow snapshot builds now update to a stable release when one comes out**, instead of staying on snapshots indefinitely. They stay on the snapshot channel afterwards, so once development moves past that release you carry on receiving snapshots as before. A Flatpak picks this up on its next `flatpak update`.
- (Linux, AppImage only) **If you are running a snapshot AppImage from before this release, download it once by hand** — from this release page — and replace your existing file. Snapshot AppImages now share a single update channel instead of one per branch, and which channel an AppImage follows is fixed when it is built, so an existing file cannot move itself across. Until you replace it, PCPanel keeps telling you a new version is available and "Update & restart" appears to do nothing: it is checking the channel that no longer receives builds. Stable AppImage downloads are unaffected, and once you have replaced the file this is a one-time thing.
- #149 - **Dials and sliders are no longer limited to linear or logarithmic.** A new **Curves** page in the settings lets you shape how far you turn a knob into how much you get. Every curve has an **Amount** slider that slides the response smoothly between straight-through and a steep logarithmic taper — so if linear wastes the top half of your slider and logarithmic wastes the bottom half, you can settle anywhere in between. Negative amounts flip it around, putting the fine control at the top of the throw instead.
- For full control, switch a curve to **Custom** and drag its shape by hand: drag a point, click the graph to add one, right-click to remove one. Curves are named and reusable, so you can set one up once and use it on as many knobs and sliders as you like.
- A dial action's **Input mapping** graph now draws the whole picture: the curve the control is set to, the part of the throw the action ignores dimmed out, and where **Trim min/max** bounds the result. The curve you configured is drawn faintly behind it, so you can see what narrowing Start/End costs.
- **Linear** and **Logarithmic** are still there and unchanged, and controls you have already set up keep behaving exactly as before. You can now edit those two as well — retuning **Logarithmic** changes every control that uses it at once, which is the quickest way to adjust everything in one go, and **Reset** puts it back. Pick a curve per control on its control page.
- Fixed the **Wave Link** integration silently dropping out after a PC restart or resume from sleep and staying dead until you restarted PCPanel. The connection could be left looking alive when it wasn't — so nothing tried to reconnect — and Wave Link actions quietly did nothing. PCPanel now detects a stalled connection and reconnects on its own.
- **Wave Link now shows as connected only when it can actually be controlled.** The indicator used to go green as soon as PCPanel had loaded a channel list, and that list stays around after the connection drops — so Wave Link could sit there looking connected while every action did nothing. It now reports the live connection, and Wave Link actions refuse and say why instead of disappearing silently.
- **Wave Link now recovers on its own when it stops accepting what PCPanel sends.** A connection can keep receiving from Wave Link while nothing PCPanel sends gets through — the integration reads as connected, and every dial and button quietly does nothing until you restart PCPanel. PCPanel now treats commands Wave Link never answers as a dead connection and reconnects.
- Fixed **Wave Link staying uncontrollable for minutes after you start the PC**, until you restarted PCPanel. Wave Link takes a while to come up on a cold boot, and every attempt to reach it before then made PCPanel wait longer before trying again — so by the time Wave Link was finally ready, PCPanel had settled into checking only once every few minutes. It now spots the moment Wave Link starts up and connects right then.
- Fixed Wave Link actions being sent into a connection that had opened but never finished starting up. Wave Link can accept the connection and then not answer PCPanel's opening questions, which left PCPanel holding a connection it could not use — the actions went out and nothing happened. They now wait until the connection is genuinely usable.
- #152 - (Windows) Fixed **every knob and button dying after the PC wakes from sleep**, until PCPanel was restarted. The lights and the overlay carried on as normal, so it looked like only the volume had stopped — but keystrokes, media keys and profile switches were dead too. The burst of audio changes Windows reports on waking could leave the audio system locked, and everything queued behind it.
- Fixed PCPanel reconnecting to Wave Link every twenty seconds indefinitely when Wave Link kept accepting connections without answering. It now spaces the attempts out, and writes down which question went unanswered so the cause is visible in the log rather than being guessed at.
- Wave Link messages are now sent one after another instead of all at once. A rapid burst — a dial being turned quickly, or the batch of questions PCPanel asks the moment it connects — could have part of it dropped on the way out, with no sign that anything had gone missing.
- **Fixed every dial and button on the panel going dead after starting the PC, until PCPanel was restarted.** The panel looked perfectly alive — the dials still moved in the interface and the volume overlay still popped up — but nothing actually happened, for Wave Link, app volume, device volume, focus volume and buttons alike. In the moments after a boot, while Windows is still reporting its audio devices, PCPanel's audio layer and those notifications could each end up waiting on the other, and that froze the single thread that carries out every dial and button action. Nothing was written to the log, which is why this looked like the Wave Link integration dropping out. PCPanel no longer waits on Windows audio while holding that lock, and if any action ever does hang in future it is now named in the log instead of the panel quietly going dead.
- #151 - (Linux) **Anything that depends on the focused app now tells you when your desktop can't do it, instead of failing silently.** "Find focused app" would report only *"Could not read the focused app"*, and focus volume would do nothing at all, with not one line in the log to explain it. Reading the focused window needs help from the desktop, and only KDE Plasma (Wayland and X11) and X11 sessions on any desktop can give it — on a **non-KDE Wayland session such as GNOME there is no way for an application to ask**, so these features cannot work there. PCPanel now says exactly that, in the message the interface shows, in a desktop notification, and in the log — naming your desktop session and quoting what each helper reported.
- #151 - (Linux) **A bug report now carries all of that on its own.** Reports include a **Focused-window detection** section listing your desktop, session type and the live result of each helper, so a focus-volume problem can be diagnosed from the attachment instead of a round of questions. Previously the report gave no hint of which desktop was even in use. That section names the **title of the window that was focused** — it is what identifies Wine/Steam games, whose window class is replaced by a Steam id — so it can name a document, page or conversation. It is on its own labelled line, shortened, only included when you leave **System information** ticked, and the report dialog now says so: check it before attaching.
- #151 - **Fixed the app picker not showing programs you started after opening the PCPanel window** — the reason "close and reopen PCPanel" was the only thing that helped. The list of running programs was fetched once when the page was opened and then kept, so opening "Add app" showed you the programs that were running back then, however long ago that was. Reopening PCPanel only worked because it opens a fresh page. The list is now re-read each time you open the picker.
- (Linux) Note that on Linux this list can only contain programs that are **playing sound right now** — on Windows it is every running program. A program that is open but silent has no audio stream for PCPanel to find, so it will not appear until it plays something.
- #151 - (Linux, Flatpak) **Fixed the media keys (play/pause, next, previous) never finding a player.** Every media action failed with *"No MPRIS media player found"* even with music playing, because the sandbox hid the running players from PCPanel.
- (Linux) A stuck helper can no longer hold up the panel: resolving the focused window now gives up after three seconds instead of waiting forever.
- #150 - Fixed **"Could not save assignment"** appearing on every change to a dial or button — including removing an action — with nothing saved, while the rest of the interface kept working normally. It happened when the active profile's name contains a `#`, `?` or `/`, and also when the profile the device was set to was no longer there. Both now save, and a profile file in the second state puts itself right the next time PCPanel starts, so there is nothing to rename or set up again.
- A bug report now records **what every part of PCPanel was doing at the moment you made it**. When the panel goes dead while the interface carries on as normal, that is the one piece of evidence that names the action which got stuck — the last time this happened it took a release to find without it. It is included when you leave **System information** ticked, and describes only the program's own workings: no file names, no window titles, nothing you typed.

## [2.0.84]

- The entire user interface has been rebuilt from the ground up with a brand-new design — a custom dark theme replacing the old windows, covering the device view, action assignment, lighting and settings.
- The underlying framework has been replaced to be more efficient. On my local machine, this results in 100mb memory usage instead of 500mb (for both Windows and Linux).
- The app now supports controllers beyond PCPanel through a generalized device layer. PCPanel hardware works exactly as before (and with zero setup), while other devices can be added and bound to the same actions through the same UI.
    - **Deej** — add the open-source Arduino serial volume mixer by its serial port; its sliders map to the same actions as PCPanel dials (no buttons/lights).
    - MIDI controller support is in progress.
- Turn a dial or slider into a multi-position **stepped switch**: split its travel into ranges, give each range its own actions (any number) and its own LED feedback colour. The actions fire the moment you move *into* a range (moving within a range does nothing), and gaps between ranges act as dead-zones. Put a *Switch profile* action on each position to flip between many profiles from a single dial.
- New per-device **base layer**: mark one profile as the fallback used for any control the active profile leaves unconfigured or unlit — actions, lighting and mute colours included. Inherited actions appear on the on-screen device as a dashed chip you can click to edit in place. Combined with a stepped switch on the base layer, a single profile-selector dial keeps working in every profile.
- A **Brightness** dial now controls the global LED brightness as a live runtime value: it wins over each profile's saved brightness and stays put across profile switches, so you configure it once (in any profile) and it governs everywhere. When more than one is configured, the best is chosen automatically.
- The per-control "change colour when muted" option is now an explicit on/off toggle, so black (`#000000`) is a usable muted colour instead of being treated as "off".
- Restored **System Sounds** as a target for the App volume and App mute actions (it had gone missing from the app picker).
- The Device volume and Device mute actions now offer a selectable **Default device** option, so an empty choice is shown and selectable as the default output device.
- The on-screen volume **overlay icon** is now freely choosable per control: pick any running app's icon, or **upload your own image** (set it under a control's *Overlay icon* picker). Picking an app icon also works again — a chosen app icon previously failed to show on the overlay (#121, #122).
- Java 25 is now required to run the software. The installer will include it.
- The Windows installer now closes a running PCPanel by itself before updating, instead of asking you to close it manually — it shuts the running instance down cleanly (saving your settings) and only force-closes it as a fallback.
- **One-click updates on Windows and Linux**: when a new version is available, the notification's "Update & restart" button installs it and restarts the app for you — no manual download or clicking through an installer. On **Windows** it runs the installer silently (keeping your existing install location); a **Linux AppImage** updates itself in place, downloading only the changed parts; and a **Flatpak** updates from its hosted repository. The page you started from reconnects on its own and confirms with a short "up to date" dialog. On macOS the notification still links to the download page.
- New update settings (under "Check for updates on startup"): **Automatically install updates** (Windows and Linux, off by default) applies a newer version on startup without any prompts; and **Check for pre-release versions** opts in to in-development snapshot builds rather than only stable releases.
- **Linux install for automatic updates**: install the Flatpak from the hosted repository via a `.flatpakref` link to get background updates through your software centre (GNOME Software / KDE Discover) or `flatpak update`; the AppImage self-updates through its bundled updater. A one-shot `.flatpak` bundle is still attached to each release but does not receive updates. See [linux.md](linux.md#updating).
- The Settings → Debug page has a new **Check for updates now** button that runs the update check on demand — surfacing the new-version popup if one is found, or confirming you're already up to date.
- #87 - Experimental macOS support (community contributed by Choaterboater, ported to the new Quarkus build). Device volume/mute/default-device switching via Core Audio, keystrokes, shortcuts and Music/Spotify media control. See the [macOS instructions](mac.md).
- (macOS) **Per-application volume / mute and focused-app volume**, on macOS 14.4 or newer, implemented natively with Core Audio process taps — no extra software needed. PCPanel taps the target app's audio and re-renders it at the dial's volume; returning the dial to full volume detaches from the app's audio path entirely. Requires the *System Audio Recording* permission (macOS prompts on first use). Tested with automated audio-measurement tests and a native build on GitHub's macOS (Apple Silicon) CI runners, but **not yet on an actual Mac in real use** — treat it as experimental and please report how it behaves.
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
- Added support for **Discord** — control your Discord voice state from your dials and buttons. Discord only grants voice control to an application's owner, so each user registers their own free Discord app once and pastes its Client ID + Secret on the Discord settings page (a step-by-step setup checklist is shown in-app); it connects over Discord's local IPC — no bot, no token in the cloud.
    - **Mute & deafen** buttons — mute/unmute or deafen/undeafen yourself, or locally mute another member (only changes what *you* hear). One Mute command with a target picker: yourself or any user.
    - **Volume** dials — your microphone, your output (how loud you hear everyone), or how loud you hear a specific member, each with an optional *unmute/undeafen when changed*.
    - **Join / leave** a voice channel from a button (pick from your servers' voice channels).
    - **Screen share** and **toggle camera** buttons — share via Discord's own picker ("Choose in Discord"), the focused window, or a specific app. Per-window sharing by PID is best-effort (it uses an undocumented Discord RPC command); the picker mode is reliable.
    - The per-control **mute colour** follows your Discord self-mute, self-deafen and a user's local-mute state — including when you toggle it in Discord directly.
    - Target pickers are searchable and list the members you've controlled plus, optionally, your **Discord friends** (needs the Social SDK Terms accepted for your app).
- New General setting **Focus volume skips controlled apps** (off by default): when the focused app is already controlled elsewhere — an App-volume action on another control, or a Wave Link channel — the focused-app volume dial leaves it alone instead of fighting that dedicated control.
- #49 - New **Focus Volume Override** settings tab: redirect the *Focused-app volume* dial elsewhere for chosen apps. Each rule has one or more source apps; when any of them is focused the dial drives the rule's targets instead. Targets can be any volume action (App/Device volume, Wave Link channel, OBS source, generic HTTP/MQTT/OSC output, …) and are all controlled at once, on every audio device the target plays on. An optional **also control the source app** toggle covers apps that don't play audio themselves but spawn a helper that does — point Steam at `steamwebhelper.exe` and leave the silent launcher alone, or control every browser at once from one dial. A **detect focused app** button captures whatever Windows reports as focused (which often isn't the launcher — Steam's window is `steamwebhelper.exe`), so picking the right source isn't guesswork.
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
- #88 - (Linux) Focus volume now works for sandboxed (Flatpak) apps. Flatpak apps report a sandbox-internal PID to PipeWire that never matched the host PID from KWin, so the focused-app knob silently did nothing; it now also matches the focused window's app id (`pipewire.access.portal.app_id`) across the sandbox boundary.
- (Linux) `kdotool` is now **bundled** with the `.deb`, AppImage and Flatpak, so focus volume works out of the box on KDE Plasma (Wayland and X11) without installing anything. Inside the Flatpak it runs in the sandbox and drives the host KWin over D-Bus. `kdotool` covers X11 too, so `xdotool` is no longer needed alongside it. If no window tool is available the app now logs a clear warning and shows a desktop notification instead of failing silently.
- (Linux) The version shown in the UI now includes the build number for official GitHub builds (e.g. `v2.0.123`) instead of just `v2.0-SNAPSHOT`.
- There is now a **Quit** button in the settings (General tab), so you can stop the app from the UI on any platform instead of killing it from a terminal — handy on macOS, which has no tray icon yet (#104).
- When no device is connected the UI shows a clear "No PCPanel connected" state with platform-specific help (the Linux device-access rule, the macOS Input Monitoring permission) instead of an endless spinner (#104).
- First run now opens the UI in your browser with a short welcome and a link to the setup instructions, and there's a new General setting to **open the UI in the browser whenever the app starts** (off by default — it otherwise just sits in the tray). On Windows the browser also opens once right after installing.
- #105 - (macOS) Fixed every Core Audio volume and mute action failing with a JNA reflection error in the packaged app (`ByteByReference` was not registered for the native image).
- #100 - (Linux) The Wayland tray now has a proper right-click menu — **Open PCPanel**, **Open settings folder** (the data dir holding your `profiles.json`, with `logs/` inside it) and **Quit** — matching the Windows tray, instead of quitting the app on a right or middle click.
- #106 - (macOS) Fixed the audio device dropdown ignoring clicks: picking a device now actually selects it. The option list could rebuild mid-click and drop the click; it no longer does (also fixes the Wave Link channel/mix dropdowns).
- #107 - (Linux) Fixed the system tray not appearing in the Flatpak — the sandbox was missing permission to own its tray name.
- #107 - (Linux) Fixed devices being detected but never opening. The bundled HID library now uses the **hidraw** backend, which needs a `hidraw` udev access rule. The `.deb` installs it automatically; AppImage/Flatpak/manual installs must add the new `hidraw` lines to their rule (see [linux.md](linux.md)) — a usb-only rule from an older version is no longer enough.

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

# CDI event catalog

This app wires its subsystems together largely through the **CDI event bus**
(`jakarta.enterprise.event.Event#fire` + `@Observes`) rather than direct calls. That keeps modules
decoupled, but there is no compile-time "who handles this?" link, so this file is the map. Keep it
up to date when you add or remove an event or an observer.

> Note: several producers fire through a generic `Event<Object>` (e.g. `SaveService`, `Voicemeeter`,
> `DeviceCommunicationHandler`), so the firer is not always greppable by event type. The tables below
> list the primary producer(s); search for the event's class name to find every observer.

## Lifecycle (Quarkus framework events)

| Event | Fired by | Observed by |
|-------|----------|-------------|
| `StartupEvent` | Quarkus | `SaveService` (priority 1, fires the initial `SaveEvent`), `DeviceScanner`, `AppShutdownState`, `TrayInitializer`, `SndCtrlPulseAudio`, `OverlayDemoTrigger` |
| `ShutdownEvent` | Quarkus | `SaveService`, `DeviceScanner`, `AppShutdownState`, `AlertLighting` and `AlertService` (priority 1: stop notification-light frames and relights before the lights-off), `SleepDetector` |

## Hardware input

| Event | Fired by | Observed by |
|-------|----------|-------------|
| `PCPanelControlEvent` | `InputInterpreter` | `CommandDispatcher` (runs the configured commands), `Overlay` |
| `TaskbarFlashEvent` | `WindowsShellHookMonitor` | `AlertService` (notification lights) |
| `TakeoverPendingEvent` | `InputInterpreter` (via `SoftTakeover`) | `Overlay` ("move to N% to take over") |
| `ButtonFeedbackEvent` | button actions, from inside the press's `TemplateContext`: the mute actions, *Set default device*, *Cycle default device*, *Send app to audio device*, *Switch profile*, *Run dial actions at a level* | `Overlay` (shows what the button did, unless the button's actions carry a typed overlay text) |
| `ButtonClickEvent` | `InputInterpreter` | `InputInterpreter` (self, click resolution), `MqttDeviceService` |
| `DeviceCommunicationHandler.KnobRotateEvent` | `DeviceHolder` (re-fires from the HID handler) | `InputInterpreter`, `EventBroadcaster` (→ UI), `MqttDeviceService`, `OSCService` |
| `DeviceCommunicationHandler.ButtonPressEvent` | `DeviceCommunicationHandler` | `InputInterpreter`, `EventBroadcaster` (→ UI), `MqttDeviceService`, `OSCService` |

## Device lifecycle

| Event | Fired by | Observed by |
|-------|----------|-------------|
| `DeviceScanner.DeviceConnectedEvent` | `DeviceScanner` | `DeviceHolder` |
| `DeviceScanner.DeviceDisconnectedEvent` | `DeviceScanner` | `DeviceHolder`, `EventBroadcaster` (→ UI) |
| `DeviceHolder.DeviceFullyConnectedEvent` | `DeviceHolder` | `EventBroadcaster` (→ UI), `MuteColorService`, `MqttDeviceService` |

## Profile & configuration

| Event | Fired by | Observed by |
|-------|----------|-------------|
| `SaveService.SaveEvent` | `SaveService` (on load and after saves) | `DeviceHolder`, `Overlay`, `MqttService`, `MqttDeviceService`, `OSCService`, `WaveLinkService`, `ShortcutHook`, `SleepDetector` (relights if sleep detection was switched off while dark), `SonarService` (recomputes the poll in-use gate from every profile's commands) |
| `ProfileSwitchedEvent` | `Device`, `DeviceResource` | `EventBroadcaster` (→ UI), `MuteColorService` |
| `LightingChangedEvent` (`EventBroadcaster.LightingChangedEvent`) | `DeviceResource` | `EventBroadcaster` (→ UI) |
| `LightingChangedToDefaultEvent` | `Device`, `DeviceResource` | `MuteColorService`, `VoiceMeeterMuteService` |
| `VisualColorsChangedEvent` (`EventBroadcaster.…`) | `DeviceResource` / `CommandsResource` / `MuteColorService` | `EventBroadcaster` (→ UI) |
| `KnobSettingChangedEvent` (`EventBroadcaster.…`) | `DeviceResource` | `EventBroadcaster` (→ UI) |
| `AssignmentChangedEvent` (`EventBroadcaster.…`) | `DeviceResource`, `CommandsResource` | `EventBroadcaster` (→ UI) |
| `DeviceRenamedEvent` (`EventBroadcaster.…`) | `DeviceResource` | `EventBroadcaster` (→ UI) |
| `GlobalBrightnessChangedEvent` | `MqttDeviceColorService` | `MqttDeviceService` |

## Native audio

| Event | Fired by | Observed by |
|-------|----------|-------------|
| `AudioDeviceEvent` | `AudioDevice` / `OsxAudioDevice` | `MuteColorService` |
| `AudioSessionEvent` | `AudioSession` / `WindowsAudioDevice` | `MuteColorService`, `NewSessionVolumeService` |
| `LinuxDeviceChangedEvent` | `PulseAudioEventListener` | `SndCtrlPulseAudio` |
| `LinuxSessionChangedEvent` | `PulseAudioEventListener` | `SndCtrlPulseAudio` |

## Integrations

| Event | Fired by | Observed by |
|-------|----------|-------------|
| `OBSConnectEvent` | `OBS` | `MuteColorService`, `ObsConnectedVolumeService` |
| `OBSMuteEvent` | `OBS` (from the websocket client callback) | `MuteColorService` |
| `VoiceMeeterConnectedEvent` | `Voicemeeter` | `VoiceMeeterConnectedVolumeService` |
| `VoiceMeeterDirtyEvent` | `Voicemeeter` | `VoiceMeeterMuteService` |
| `VoiceMeeterMuteEvent` | `VoiceMeeterMuteService` | `VoiceMeeterMuteResolver` (mute-colour) |
| `WaveLinkChangedEvent` | `WaveLinkService` (Wave Link state incl. mute changed) | `MuteColorService` |
| `DiscordChangedEvent` | `DiscordService` (Discord voice/mute/deafen state changed) | `MuteColorService` |
| `MuteOverridesDirtyEvent` | mute-colour resolvers (e.g. `VoiceMeeterMuteResolver` after caching a mute change) | `MuteColorService` |
| `MqttStatusEvent` | `MqttService` | `MqttDeviceService` |
| `SonarChangedEvent` | `SonarService` (a local write changed a mute; a poll picked up a state change; the mode went away or Sonar was switched off) | `MuteColorService` |

## System & UI

| Event | Fired by | Observed by |
|-------|----------|-------------|
| `SystemEvent` (sleep/wake/lock/display-off/display-on) | `WindowsSystemEventService` / `LinuxSystemEventService` / `MacSystemEventService` | `SleepDetector` |
| `PanelsDarkEvent` (`dark`, `awake`: dark for a lock/display-off only, not sleep) | `SleepDetector` (before the lights-off for sleep/lock/display-off; again when a dark PC goes to sleep or wakes still dark; after the relight on wake) | `AlertLighting` (no notification-light frames while dark; repaints on wake), `AlertService` (no notification-light relights while dark; with *Keep notification lights on while locked* and `awake`, asks `SleepDetector.showDarkFrame` instead), `VisualizerService` (wakes to park, or with *Keep the music visualizer running while locked* and `awake`, to carry on onto dark frames) |
| `WindowFocusChangedEvent` | focus watchers | `DeviceHolder`, `ProfileWindowFocusService`, `MuteColorService` (a focused-app control's muted colour tracks the app that now has focus), `AlertService` (clears the focused app's taskbar-flash lights) |
| `ShowMainEvent` | `FileChecker`, `StatusNotifierItemImpl`, `TrayServiceWin`, `DBusMenuImpl`, `CommandOpenWebUi`, `StartupOnboarding` | `ShowMainService` (optional `redirect` lands the browser on a named in-app path, e.g. the tray's "Report a problem" → `/?report=1`) |
| `OpenFolderEvent` | `TrayServiceWin`, `DBusMenuImpl` (tray "Open settings folder") | `ShowMainService` (reveals the folder in the OS file manager) |
| `NewVersionAvailableEvent` | `VersionChecker` | (UI/notification consumers) |
| `AlertsLitEvent` | `AlertService` (when the notification lights showing change, previews included) | `EventBroadcaster` (→ UI `alerts_lit`, replayed to clients that connect later) |

---

The `Ws*` events under `rest/model/ws/` are **not** CDI events — they are the JSON DTOs
`EventBroadcaster` serialises and pushes to the Angular UI over the `/ws/events` websocket. The
backend→UI flow is always: a CDI event above → `EventBroadcaster` `@Observes` it → builds the
matching `Ws*` DTO → `EventWebSocket.broadcast(...)`.

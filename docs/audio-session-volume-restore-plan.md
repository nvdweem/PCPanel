# Audio Session Volume Restore – Priority Coordinator

## Problem Statement

When a new OS audio session starts (e.g. a browser begins playing sound), the software attempts to restore the preset volume that is configured for that process on the panel.

**Three problems exist in the current implementation:**

1. **Missing focus-volume restore** – `SetNewSessionVolumeService` only handles `CommandVolumeProcess`. If a dial is configured with `CommandVolumeFocus` and the focus application starts a new audio session, nothing is restored.

2. **No priority / conflict resolution** – If an application is *both* the current OS focus app *and* has a dedicated `CommandVolumeProcess` mapping, both code-paths could fire. The explicit per-process mapping should always win.

3. **WaveLink routing conflict** – If the focus application's audio is routed through the WaveLink mixer, calling `ISndCtrl.setFocusVolume()` targets the raw OS session and bypasses WaveLink entirely. In that case the correct action is to trigger the matching `CommandWaveLinkChangeLevel` dial.

---

## Solution

Replace `SetNewSessionVolumeService` with a new `NewSessionVolumeCoordinatorService` that evaluates the three restore strategies in priority order, stopping at the first level that produces a match.

### Priority chain

```
Priority 1 → Direct process control   (CommandVolumeProcess matches session exe)
Priority 2 → WaveLink focus control   (focus app is in a WaveLink channel → trigger CommandWaveLinkChangeLevel)
Priority 3 → OS focus control         (CommandVolumeFocus — only when not WaveLink-managed)
```

A new `WaveLinkFocusState` bean tracks the channel-to-process mapping so the coordinator can perform the Priority-2 check without querying WaveLink at runtime.

---

## New Beans

### `NewSessionVolumeCoordinatorService`

| Attribute  | Value |
|------------|-------|
| Package    | `com.getpcpanel.cpp` |
| Stereotype | `@ApplicationScoped` |
| Superclass | `AbstractNewXVolumeService` |

**Responsibilities:**
- Observes `AudioSessionEvent` (replaces `SetNewSessionVolumeService`).
- Injects `ISndCtrl` (for `getFocusApplication()`).
- Injects `jakarta.enterprise.inject.Instance<WaveLinkFocusState>` (optional — satisfied only on Windows when WaveLink is present).
- On `ADDED` event (or `CHANGED` when `forceVolume` is set), runs the priority chain described below.

**Priority-chain pseudocode:**

```
onNewAudioSession(event):
  exe = event.session().executable().getName()   // e.g. "chrome.exe"

  // Priority 1 – Direct process mapping
  if hasCommandsOf(CommandVolumeProcess, c -> c.getProcessName().contains(exe) && deviceMatches(event, c)):
      triggerCommandsOf(CommandVolumeProcess, s -> s.filterValues(c -> isProcessAndDevice(event, c)))
      return

  focusApp = sndCtrl.getFocusApplication()       // e.g. "chrome.exe"
  if !equalsIgnoreCase(exe, focusApp):
      return                                     // session is not the focus app → nothing to do for P2/P3

  // Priority 2 – WaveLink focus channel
  waveLinkFocusState.ifPresent(wlfs ->
      wlfs.getChannelForProcess(exe).ifPresent(channelId ->
          triggerCommandsOf(CommandWaveLinkChangeLevel,
              s -> s.filterValues(c -> channelMatchesId(c, channelId)))
          // mark as handled so P3 is skipped
          handled = true
      )
  )
  if handled: return

  // Priority 3 – OS focus volume
  if hasCommandsOf(CommandVolumeFocus, _ -> true):
      triggerCommandsOf(CommandVolumeFocus, Function.identity())
```

---

### `WaveLinkFocusState`

| Attribute  | Value |
|------------|-------|
| Package    | `com.getpcpanel.wavelink` |
| Stereotype | `@ApplicationScoped`, `@WindowsBuild` |
| Purpose    | Tracks which WaveLink channel IDs currently have the focus application assigned |

**State:** `Map<String, String> channelIdToExe` (channelId → process exe name, lower-cased).

**Methods:**

| Signature | Behaviour |
|-----------|-----------|
| `addFocusToChannel(String channelId)` | Calls `sndCtrl.getFocusApplication()`, stores `channelId → exe.toLowerCase()`. |
| `Optional<String> getChannelForProcess(String exe)` | Returns the channelId mapped to `exe` (case-insensitive), or `Optional.empty()`. |
| `boolean isProcessInWaveLinkChannel(String exe)` | Convenience: `getChannelForProcess(exe).isPresent()`. |

**Dependencies injected:** `ISndCtrl`.

---

## Modified Classes

### `AbstractNewXVolumeService`

1. **Extract `buildCommandStream(Class<T>)`** – move the `StreamEx` pipeline from `triggerCommandsOf` into a private helper `buildCommandStream(Class<T> clazz)` that returns `EntryStream<DeviceAndDial, T>`. `triggerCommandsOf` delegates to it.

2. **Add `hasCommandsOf(Class<T>, Predicate<T>)`** –
   ```
   protected <T extends Command> boolean hasCommandsOf(Class<T> clazz, Predicate<T> filter):
       return buildCommandStream(clazz).values().anyMatch(filter)
   ```
   This lets the coordinator check for the existence of a command type *before* firing events.

---

### `CommandWaveLinkAddFocusToChannel.execute()`

After the existing `getWaveLinkService().addCurrentToChannel(id)` call, also update the in-process state tracker:

```
CdiHelper.getBean(WaveLinkFocusState.class).addFocusToChannel(id);
```

This keeps `WaveLinkFocusState` in sync whenever a user presses a button that assigns the focus app to a WaveLink channel.

> **Note:** `WaveLinkFocusState` is `@WindowsBuild`-scoped. The call is guarded with an `Instance<>` check inside `CommandWaveLinkAddFocusToChannel`, consistent with how the coordinator calls it optionally.

---

### `SetNewSessionVolumeService`

- This class becomes redundant once `NewSessionVolumeCoordinatorService` is in place.
- **Delete** `SetNewSessionVolumeService.java`.

---

## Priority-Logic Detail

```
Given: AudioSessionEvent event, String exe = session.executable().getName()

STEP 1 – Process mapping check
  matched = hasCommandsOf(CommandVolumeProcess,
                c -> c.getProcessName().contains(exe) && deviceMatches(event, c))
  if matched:
    triggerCommandsOf(CommandVolumeProcess,
                      s -> s.filterValues(c -> isProcessAndDevice(event, c)))
    STOP

STEP 2 – Is this the focus application?
  focusApp = sndCtrl.getFocusApplication()
  if focusApp == null || !exe.equalsIgnoreCase(new File(focusApp).getName()): STOP

STEP 3 – WaveLink channel check (Windows/WaveLink only)
  if waveLinkFocusStateInstance is present:
    channelId = waveLinkFocusState.getChannelForProcess(exe)
    if channelId.isPresent():
      triggerCommandsOf(CommandWaveLinkChangeLevel,
                        s -> s.filterValues(c -> c.getCommandType() in {Input, Channel}
                                             && channelId.get().equals(c.getId1())))
      STOP

STEP 4 – OS focus volume
  triggerCommandsOf(CommandVolumeFocus, Function.identity())
  STOP
```

---

## Implementation Steps

1. **Modify** `src/main/java/com/getpcpanel/commands/AbstractNewXVolumeService.java`
   — Extract `buildCommandStream(Class<T>)` helper; add `hasCommandsOf(Class<T>, Predicate<T>)` method.

2. **Create** `src/main/java/com/getpcpanel/wavelink/WaveLinkFocusState.java`
   — `@ApplicationScoped`, `@WindowsBuild`; inject `ISndCtrl`; implement `addFocusToChannel`, `getChannelForProcess`, `isProcessInWaveLinkChannel`.

3. **Modify** `src/main/java/com/getpcpanel/wavelink/command/CommandWaveLinkAddFocusToChannel.java`
   — In `execute()`, after `addCurrentToChannel(id)`, call `WaveLinkFocusState.addFocusToChannel(id)` via an `Instance<WaveLinkFocusState>` guard using `CdiHelper`.

4. **Create** `src/main/java/com/getpcpanel/cpp/NewSessionVolumeCoordinatorService.java`
   — `@ApplicationScoped`; extends `AbstractNewXVolumeService`; inject `ISndCtrl`, `SaveService`, and `Instance<WaveLinkFocusState>`; implement `onNewAudioSession(@Observes AudioSessionEvent)` with the full priority chain.

5. **Delete** `src/main/java/com/getpcpanel/cpp/SetNewSessionVolumeService.java`
   — Remove file; confirm no remaining imports or references exist.

---

## Further Considerations

- **`WaveLinkFocusState` persistence** – The map is in-memory only and is lost on restart. Consider whether assignments should be persisted in the save file (`WaveLinkSettings`) or re-populated from WaveLink's own state on reconnect.
- **Multi-device interaction** – `triggerCommandsOf(CommandVolumeFocus, …)` fires for *every* dial configured as focus volume across all devices. This is consistent with the existing `CommandVolumeProcess` behaviour.
- **Null-guard for `getFocusApplication()`** – On Linux, `ISndCtrl.noOp()` returns `null` for `getFocusApplication()`. The coordinator null-checks this before using it.

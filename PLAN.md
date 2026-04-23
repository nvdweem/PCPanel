# PCPanel — WebSocket-Driven Reactive Device State Plan
## Goal
Replace the current HTTP-poll-on-connect pattern with a WebSocket-driven **snapshot + patch** model.
On WebSocket connect, the backend pushes a full `device_snapshot` for each connected device.
Subsequent mutations trigger typed patch events. The Angular frontend maintains a single
`signal<DeviceSnapshot[]>` in a new `DeviceStateService`, and all components derive their view
via `computed()`. HTTP remains **only for mutations** (save, rename, switch profile, set lighting).
---
## Why the current approach does not compose with signals
- `HomeFacade`, `DeviceComponent`, and `LightingComponent` each independently trigger HTTP requests
  on load to assemble device state: `listDevices` + `getDevice` + `getLighting` + N x `getDialCommands`
  + N x `getButtonCommands`.
- The results land in local component signals that are not shared — switching pages discards and re-fetches everything.
- WebSocket events are thin operational notifications that cannot bootstrap the UI on reconnect.
- Signal derivation (`computed`) works best with a single authoritative upstream — HTTP fragmentation defeats this.
---
## Planned WebSocket message types
### Existing (keep as-is)
| type | payload | trigger |
|---|---|---|
| `device_connected` | serial | device plugged in |
| `device_disconnected` | serial | device unplugged |
| `knob_rotate` | serial, knob, value | physical knob turn |
| `button_press` | serial, button, pressed | physical button |
### New (to add)
| type | payload | trigger |
|---|---|---|
| `device_snapshot` | full `DeviceSnapshotDto` | on WS `@OnOpen`, one per connected device (to that connection only) |
| `profile_switched` | serial, profileName, `ProfileSnapshot` | after `switchProfile` saves |
| `lighting_changed` | serial, `LightingConfig` | after `setLighting` saves |
| `assignment_changed` | serial, kind (dial/button/dblbutton), index, `CommandsWrapper` | after set-commands saves |
| `device_renamed` | serial, displayName | after `renameDevice` saves |
---
## Step 1 — Define DeviceSnapshot DTO (backend + frontend)
**Backend** (`com.getpcpanel.rest.dto`):
- Add `DeviceSnapshotDto` record containing all `DeviceDto` fields plus:
  - `LightingConfig lightingConfig`
  - `ProfileSnapshot currentProfileSnapshot` (active profile dial/button/dblbutton data)
  - `Map<Integer, Integer> analogValues` (last-known knob values for UI restore on reconnect)
**Frontend** (`src/main/webui/src/app/models/models.ts`):
- Add `DeviceSnapshot` interface mirroring `DeviceSnapshotDto`.
- Add `ProfileSnapshot` interface (dial/button command maps).
- Expand `WsEvent` discriminated union to include all new event types.
- Expand `isWsEvent()` guard in `EventService` to validate new shapes.
---
## Step 2 — Backend: emit device_snapshot on WebSocket open
**File**: `src/main/java/com/getpcpanel/rest/EventWebSocket.java`
- In `@OnOpen`, inject `DeviceHolder` and `SaveService` (or equivalent state store).
- For each currently connected device, build a `DeviceSnapshotDto` and send it to that session only
  (not broadcast). Use `WebSocketConnection.sendText(json)` returning `Uni<Void>`.
- Note: `@OnOpen` in `quarkus-websockets-next` should return `Uni<Void>` for reactive send safety.
---
## Step 3 — Backend: emit patch events after mutations
**File**: `src/main/java/com/getpcpanel/rest/DeviceResource.java`
After each mutating endpoint, fire a CDI event so the broadcaster pushes a WS patch:
| Endpoint | CDI event | WS message emitted |
|---|---|---|
| `renameDevice` | `DeviceRenamedEvent(serial, name)` | `device_renamed` |
| `switchProfile` | `ProfileSwitchedEvent(serial, name, snapshot)` | `profile_switched` |
| `setLighting` | `LightingChangedEvent(serial, config)` | `lighting_changed` |
| `setDial` / `setButton` / `setDblButton` | `AssignmentChangedEvent(serial, kind, index, cmds)` | `assignment_changed` |
**File**: `src/main/java/com/getpcpanel/rest/EventBroadcaster.java`
- Add `@Observes` handlers for each new CDI event type, broadcasting to all connected sessions.
---
## Step 4 — Frontend: create DeviceStateService
**New file**: `src/main/webui/src/app/services/device-state.service.ts`
```typescript
@Injectable({ providedIn: 'root' })
export class DeviceStateService {
  private readonly _devices = signal<DeviceSnapshot[]>([]);
  readonly devices = this._devices.asReadonly();
  readonly ready = signal(false); // true after first device_snapshot received
  constructor() {
    inject(EventService).events$
      .pipe(takeUntilDestroyed())
      .subscribe(event => this.apply(event));
  }
  snapshotFor(serial: string): Signal<DeviceSnapshot | null> {
    return computed(() => this._devices().find(d => d.serial === serial) ?? null);
  }
  private apply(event: WsEvent): void {
    switch (event.type) {
      case 'device_snapshot':
        this._devices.update(ds => upsertBySerial(ds, event.snapshot));
        this.ready.set(true);
        break;
      case 'device_disconnected':
        this._devices.update(ds => ds.filter(d => d.serial !== event.serial));
        break;
      case 'device_connected':
        break; // snapshot follows immediately from backend
      case 'profile_switched':
        this._devices.update(patchSerial(event.serial, d => ({
          ...d, currentProfile: event.profileName,
          currentProfileSnapshot: event.profileSnapshot,
        })));
        break;
      case 'lighting_changed':
        this._devices.update(patchSerial(event.serial, d => ({...d, lightingConfig: event.lightingConfig})));
        break;
      case 'assignment_changed':
        // immutable update of one slot in currentProfileSnapshot.dialData / buttonData
        break;
      case 'device_renamed':
        this._devices.update(patchSerial(event.serial, d => ({...d, displayName: event.displayName})));
        break;
      case 'knob_rotate':
        this._devices.update(patchSerial(event.serial, d => ({
          ...d, analogValues: {...d.analogValues, [event.knob]: event.value},
        })));
        break;
    }
  }
}
```
All patch operations must be immutable (spread or Map copy — never mutate the existing signal value).
---
## Step 5 — Frontend: simplify HomeFacade
**File**: `src/main/webui/src/app/pages/home/home.facade.ts`
- Remove `loadDevices()`, `loadAssignments()`, `loadLighting()` HTTP calls.
- Remove local `devices`, `lightingConfig`, `dialCommands`, `dialLabels` signals.
- Inject `DeviceStateService`.
- `devices = computed(() => deviceStateService.devices())`.
- `selectedSnapshot = computed(() => deviceStateService.snapshotFor(this.selectedSerial())())`.
- `lightingConfig = computed(() => this.selectedSnapshot()?.lightingConfig ?? null)`.
- `dialCommands = computed(() => this.selectedSnapshot()?.currentProfileSnapshot?.dialData ?? {})`.
- `switchProfile()`, `onBrightnessChange()` stay as HTTP mutations — no change.
---
## Step 6 — Frontend: simplify DeviceComponent
**File**: `src/main/webui/src/app/pages/device/device.component.ts`
- Remove the `forkJoin` HTTP load of dial/button commands in constructor.
- Inject `DeviceStateService`.
- `device = computed(() => deviceStateService.snapshotFor(this.serial())())`.
- `dialCommands = computed(() => this.device()?.currentProfileSnapshot?.dialData ?? {})`.
- `buttonCommands = computed(() => this.device()?.currentProfileSnapshot?.buttonData ?? {})`.
- Keep HTTP-only paths for `saveName()`, `switchProfile()`, `addProfile()`, `editDial()`, `editButton()`.
---
## Step 7 — Frontend: simplify LightingComponent
**File**: `src/main/webui/src/app/pages/lighting/lighting.component.ts`
- Remove `getLighting` HTTP fetch in constructor.
- Seed `configModel` from `DeviceStateService` via an `effect`:
  ```ts
  effect(() => {
    const cfg = deviceStateService.snapshotFor(this.serial())()?.lightingConfig;
    if (cfg) this.configModel.set(cfg);
  });
  ```
- Keep `save()` as HTTP PUT. After the PUT, the backend emits `lighting_changed` which updates
  `DeviceStateService` automatically — no manual signal update needed in the component.
---
## Step 8 — Migration guard (parallel paths until stable)
- During Steps 4-7, do not remove the existing HTTP fetch paths immediately.
- Components check `deviceStateService.ready()`: if `true`, use signal-derived data; if `false`,
  fall back to the existing HTTP fetch.
- Once the backend reliably emits snapshots (verified manually), remove fallback HTTP read paths
  from `HomeFacade`, `DeviceComponent`, and `LightingComponent`.
- Final cleanup: remove `listDevices()`, `getDevice()`, `getLighting()`, `getDialCommands()`,
  `getButtonCommands()` from `DeviceService`.
---
## What stays HTTP (mutations only after migration)
- `POST /device/:serial/rename`
- `POST /device/:serial/profiles/:profile/switch`
- `POST /device/:serial/profiles` (create profile)
- `PUT /device/:serial/lighting`
- `PUT /device/:serial/profiles/:profile/dials/:index`
- `PUT /device/:serial/profiles/:profile/buttons/:index`
- `PUT /device/:serial/profiles/:profile/dblbuttons/:index`
## What moves to WebSocket (state / reads)
Everything else. The frontend never HTTP-fetches device state to display it.
---
## Open questions / decisions needed
1. **Snapshot scope**: send only the active profile's assignments (small payload) or all profiles?
   Sending all profiles avoids an extra WS message on profile switch but increases snapshot size.
   Recommendation: active profile only; `profile_switched` carries the new profile's full snapshot.
2. **Analog values in snapshot**: include last-known knob values so the UI restores visual position
   on reconnect? Requires the backend to track current analog state in `DeviceHolder`.
   Recommendation: yes, add `Map<Integer, Integer> analogValues` to the snapshot DTO.
3. **@OnOpen reactive safety**: confirm `quarkus-websockets-next` `@OnOpen` supports `Uni<Void>`
   return type when sending to a single connection — may need `connection.sendText(...).subscribe()`
   instead of a blocking call to avoid blocking the event loop.

# Device-Layer Generalization Plan

> Make the app support controllers beyond PCPanel — **Deej** (serial volume mixers), **MIDI**
> controllers, and a **generic configurable** device — while keeping PCPanel a first-class,
> zero-config experience. PCPanel becomes **one device provider among several**.
>
> Status: design + phased implementation. Based on a full read of the codebase at `origin/main`
> tip `79d8fe8` (2026-06-20). File:line references are from that revision.

---

## 0. Guiding principles

1. **PCPanel stays zero-config and visually special.** Generalization adds a generic path; it must
   not regress the PCPanel UX. PCPanel ships built-in device descriptors so owners never see manual
   setup.
2. **A "device" is a capability descriptor, not a hardware model.** Everything the rest of the app
   needs to know about a device (how many analog/digital inputs, their ranges, what lights/outputs
   it has) is *data* supplied by a provider, not a `switch` on an enum.
3. **Providers supply devices.** A `DeviceProvider` owns one transport (HID, serial, MIDI) plus the
   discovery + I/O for it. PCPanel's current HID stack becomes the first provider.
4. **Normalize at the edge.** Each provider converts its raw values to the **canonical internal
   analog domain 0–255** (the value the command layer already assumes). The command/value/profile
   machinery is left untouched.
5. **Backward compatible.** Existing `profiles.json` files (PCPanel devices keyed by serial, PCPanel
   lighting blobs, FQCN command discriminators) must keep loading unchanged.
6. **Build-correctness is part of every change.** Two things break only in the native image / TS
   contract, never at compile time: GraalVM reflection metadata and the Java→TypeScript generator.
   Every type change here lists its native-image + TS-gen obligations.

---

## 1. Where PCPanel-specific assumptions live today

The coupling is concentrated in a handful of seams. (Exhaustive file:line list in §9.)

| Concern | Where it's hardcoded |
| --- | --- |
| Device catalog (3 models, VID/PID, counts, logo) | `device/DeviceType.java:13-15` — the single source of truth |
| Discovery | `hid/DeviceScanner.java:235-241` iterates `DeviceType.ALL` matching VID/PID |
| Device construction | `device/DeviceFactory.java` (3 builders) + `hid/DeviceHolder.java:54-62` (3-way `if`) |
| Per-model device classes | `device/PCPanel{Mini,Pro,RGB}Device.java` — near-identical, differ only in `deviceType()` + array size |
| Transport + framing | `hid/DeviceCommunicationHandler.java` — 64-byte HID packet, opcodes `1/2`, hid4java |
| Input conditioning | same file — debounce, rolling average, delta filter (transport-agnostic, but trapped in the HID handler) |
| Value range | `hid/DialValueCalculator.java:42` hardcodes `0,255`; `hid/InputInterpreter.java:41-43` remaps RGB `0-100→0-255` |
| Lighting protocol | `hid/OutputInterpreter.java` — `switch(DeviceType)`, `PREFIX_MINI/PRO`, all byte building |
| Lighting model | `profile/dto/LightingConfig.java` + `Single*LightingConfig` — flat PCPanel RGB bag; `defaultLightingConfig(DeviceType)` throws on unknown |
| Logo LED | `DeviceType.hasLogoLed`, `LightingConfig.logoConfig`, the Pro-only `CUSTOM_LOGO` path |
| Index geometry (knobs `[0..n)`, sliders `[n..)`) re-derived 4× | `DeviceCommunicationHandler.applyWorkaround:230`, `commands/SetMuteOverrideService.java:210,227`, `osc/OSCService.java:111`, `rest/ProVisualColorsService.java:23-25` |
| UI preview colors | `rest/ProVisualColorsService.java` — Pro-only, hardcoded 5/4/5 counts |
| DTO device shape | `rest/model/dto/DeviceDto.java`, `DeviceSnapshotDto.java` — expose `deviceType/analogCount/buttonCount/hasLogoLed` from the enum |
| Persistence identity | `profile/DeviceSave.java` — keyed by serial, **does not store the device type** (re-derived each connect) |
| Frontend layout | `devices/visual/pc-device.component.ts` + `pages/{control,lighting,device}.component.ts` branch on `isPro` and hardcode `idx>=5`, knob counts |

**Good news from the analysis:**
- Commands are already device-agnostic (they act on OS audio / OBS / VoiceMeeter / keyboard) and use
  a marker-interface + `@JsonTypeInfo(Id.CLASS)` plug-in model (`wavelink.command.**` already lives
  outside `commands.command.**`). The only PCPanel-coupled command is `CommandBrightness`.
- Identity is already an opaque `String serial` everywhere — REST paths, events, maps. Nothing
  assumes it is USB-HID-shaped, so a serial port path or a MIDI port name works as a key as-is.
- The analog index space is already unified (Pro: dials `0..4`, sliders `5..8`); the DTOs already
  send dynamic-length arrays (`analogValues`, `dialColors`, `sliderColors`). The frontend simply
  *ignores* the counts it's sent and re-hardcodes them.
- `PlatformService` (`/api/platform`) is prior art for "frontend reads capabilities from the
  backend instead of hardcoding."

---

## 2. Target architecture

```
                       ┌─────────────────────────────────────────────┐
                       │            DeviceProviderRegistry            │
                       │      (injects Instance<DeviceProvider>)      │
                       └───────────────┬─────────────────────────────┘
        ┌──────────────────────────────┼──────────────────────────────┐
        ▼                              ▼                               ▼
 PCPanelHidProvider            DeejSerialProvider              MidiProvider
 (hid4java, VID/PID)           (jSerialComm, port+baud)        (javax.sound.midi)
        │  discovers + opens transports, builds a DeviceConnection per device,
        │  attaches a DeviceDescriptor (capabilities), normalizes raw → 0-255
        ▼
 ┌──────────────────────────────────────────────────────────────────────────┐
 │  Shared core (transport-neutral, unchanged value semantics)                │
 │  • InputConditioner  (debounce / rolling-avg / delta — lifted out of HID)  │
 │  • ControlInputEvent / ButtonInputEvent  (renamed Knob/ButtonRotate)       │
 │  • InputInterpreter → DialValue → PCPanelControlEvent → CommandDispatcher  │
 │  • Device (one generic class, parameterized by DeviceDescriptor)           │
 │  • OutputRouter → per-device DeviceOutputWriter (HID lighting / MIDI / no-op)│
 │  • SaveService / Profile / Commands  (unchanged keying by serial + int idx)│
 └──────────────────────────────────────────────────────────────────────────┘
```

### 2.1 `DeviceProvider` SPI (new package `com.getpcpanel.device.provider`)

```java
public interface DeviceProvider {
    String id();                       // "pcpanel" | "deej" | "midi" | "generic"
    DiscoveryMode discoveryMode();     // AUTO (HID/MIDI enumerate) | MANUAL (user picks port+baud)
    void start();                      // begin discovery; fire DeviceConnectedEvent per device
    void stop();
    // Manual providers (Deej): the UI calls this to add a device by parameters.
    default Optional<String> addManual(ManualDeviceRequest req) { return Optional.empty(); }
}
```

- Beans are plain `@ApplicationScoped`, **all present in every build**, discovered via
  `Instance<DeviceProvider>` in a `DeviceProviderRegistry`. **Do not** use the `@WindowsBuild`/
  `@IfBuildProperty` stereotypes — those exist to *exclude* code that can't link on a platform
  (libawt on macOS, PulseAudio on Windows); device providers are wanted in every OS build. (A
  provider whose native lib is platform-limited may *additionally* be platform-gated, but that's the
  exception layered on top, not the selection mechanism.)
- `DeviceScanner` is refactored into `PCPanelHidProvider` (keeps all its hid4java logic: the
  open-once `computeIfAbsent`, the reconcile thread, the macOS permission check). The serial/MIDI
  providers reuse the same "register atomically per key, open once" pattern via a shared base.

### 2.2 Capability descriptor (new package `com.getpcpanel.device.descriptor`)

This replaces `DeviceType` as the runtime value carried on connect events and DTOs. Place it under a
package that the TS generator already globs (`**.dto.**`) **or** add `com.getpcpanel.device.**` to
the generator's `classPatterns` (see §7) so it flows into `backend.types.ts`.

```java
public record DeviceDescriptor(
    String providerId,                 // "pcpanel"
    String deviceKindId,               // "PCPANEL_PRO" (PCPanel) | template id | "deej" | midi port
    String displayName,                // "PCPanel Pro"
    List<AnalogInputSpec> analogInputs,
    List<DigitalInputSpec> digitalInputs,
    List<LightOutputSpec>  lightOutputs,
    List<AnalogOutputSpec> analogOutputs,
    GlobalLightingSpec     globalLighting   // nullable — only firmware-animated devices (PCPanel)
) {}

public record AnalogInputSpec(
    int index,                         // the existing flat int index (knob i, Pro slider i+5)
    String id,                         // stable key ("knob0", "slider1", "cc7.ch0")
    String label,                      // "K1" / "S1" / device-supplied
    AnalogKind kind,                   // KNOB | SLIDER | ENCODER
    int sourceMin, int sourceMax,      // raw range BEFORE normalization (255 / 1023 / 127 / 16383)
    boolean hasButton,                 // a push-button shares this index (PCPanel knob)
    Integer lightOutputIndex           // nullable: the colocated light, if any
) {}

public record DigitalInputSpec(int index, String id, String label, boolean standalone) {}

public record LightOutputSpec(
    int index, String id, String label,
    LightColorModel colorModel,        // NONE | MONOCHROME | RGB | SCALAR_0_254
    LightGroupKind group,              // DIAL | SLIDER | SLIDER_LABEL | LOGO | GENERIC
    List<String> supportedElementModes // e.g. ["NONE","STATIC","VOLUME_GRADIENT"]
) {}

public record AnalogOutputSpec(int index, String id, String label, int min, int max) {}

public record GlobalLightingSpec(
    List<String> supportedModes,       // ALL_COLOR / ALL_RAINBOW / ... / CUSTOM (PCPanel)
    boolean hasGlobalBrightness, int brightnessMin, int brightnessMax,
    boolean firmwareAnimated           // true=PCPanel firmware drives it; false=software-emulated
) {}
```

Enums: `DiscoveryMode`, `AnalogKind`, `LightColorModel`, `LightGroupKind`. (TS gen emits these as
string unions; each must be registered for native-image reflection.)

**PCPanel built-ins:** `PCPanelHidProvider` builds these descriptors from the existing `DeviceType`
enum, so the three PCPanel models keep exactly today's geometry and lighting capability matrix:
- RGB: 4 KNOB analog inputs (sourceMax 100), 4 buttons on those indices, 4 RGB dial lights, global
  modes `ALL_COLOR, SINGLE_COLOR, ALL_RAINBOW, ALL_WAVE, ALL_BREATH`, no logo.
- Mini: 4 KNOB (sourceMax 255), 4 buttons, 4 RGB dial lights, modes `ALL_COLOR/RAINBOW/WAVE/BREATH/CUSTOM`.
- Pro: 5 KNOB + 4 SLIDER (analog idx 5..8, sourceMax 255), 5 buttons, dial+slider+sliderLabel RGB
  lights + logo, modes `ALL_COLOR/RAINBOW/WAVE/BREATH/CUSTOM`.

`DeviceType` is demoted to a PCPanel-provider-internal table (VID/PID + the data to build the three
descriptors). It stays in the codebase but nothing outside `pcpanel` provider reads it.

### 2.3 Value normalization (decision: keep 0–255 internal)

The research recommended a `double` 0.0–1.0 internal value; we deliberately **keep the existing
0–255 canonical domain** because (a) `DialValueCalculator`, `KnobSetting` (0–100 trims), the log
curve (`/2.55`), and every command's `getValue()` are built around it, and (b) every existing
profile on disk is shaped for it. Switching to `double` is a large, breaking blast radius for no
user-visible gain (volume is a percentage anyway).

- Each provider normalizes raw → 0–255 at its edge, using `AnalogInputSpec.sourceMin/Max`:
  - PCPanel: identity (Mini/Pro) or `0-100→0-255` (RGB) — **this removes the `if PCPANEL_RGB`
    special-case from `InputInterpreter`** (`InputInterpreter.java:41-43`); the provider does it.
  - Deej: `raw*255/1023`.
  - MIDI 7-bit: `cc*255/127`; 14-bit: `combined*255/16383` (8-bit precision is fine for volume).
- The descriptor still carries the *source* range so the UI can show correct step sizing and so a
  future high-resolution path is a localized change.
- `DialValueCalculator`'s `0,255` literals become `descriptor.sourceMin/Max`-aware **only if** we
  ever stop normalizing at the edge; for now they stay (input is already 0–255 when it arrives).

### 2.4 Transport + input conditioning split

`DeviceCommunicationHandler` is split:
- **`InputConditioner`** (new, transport-neutral): the `KnobDebouncer`, `RollingAverageSetter`, and
  delta filter (`DeviceCommunicationHandler.java:215-415`). Any provider feeds it raw `(index,
  value, initial)`; it emits conditioned `ControlInputEvent`s. Deej needs this (10-bit pots are
  noisy — also add deej's per-slider dead-band `0.015/0.025/0.035` as a normalized option).
- **`HidDeviceConnection`** (PCPanel provider): the 64-byte framing, opcodes, reader/writer threads,
  `isConnected()` identity check. Implements a generic `DeviceConnection { void sendRaw(...); void
  close(); }`.
- Preserve the `Throwable` guard in the reader (`:194-209`) — one bad event must never kill input
  (see MEMORY: overlay/AWT can throw in native image).

### 2.5 Output / lighting capability

Staged to limit blast radius (see phases). Target shape:
- `DeviceOutputWriter` interface per provider: `sendLighting(LightingConfig)`, `sendInit()`,
  `setAnalogOutput(int index, int value)`. HID impl = today's `OutputInterpreter` byte building
  (the `PREFIX_*`/`ANIMATION_*` constants become private to it). Deej impl = no-op. MIDI impl =
  NOTE_ON velocity for pad color + CC for LED rings / `setAnalogOutput`.
- `Device.doSetLighting` (`device/Device.java:95-107`) calls the device's resolved
  `DeviceOutputWriter`, not the global `OutputInterpreter.sendLightingConfig(serial, deviceType,…)`
  switch.
- `Device.lightingCapabilities()` drives defaults: `LightingConfig.defaultLightingConfig(DeviceType)`
  (`LightingConfig.java:72-91`, which throws on unknown) becomes
  `descriptor.globalLighting().defaultConfig()` / provider-supplied. Lightless devices (Deej) get
  `null`/`NoLighting` and skip the entire lighting path; the lighting page + brightness slider hide.
- **Analog output (MIDI 0–254 LED)** is net-new everywhere: `AnalogOutputSpec`, a
  `Device.setAnalogOutput`, a `DeviceOutputWriter.setAnalogOutput`, an optional
  `CommandAnalogOutput`, and a WS `analog_output_changed` event + UI readout.

### 2.6 Persistence (self-identifying devices)

`profile/DeviceSave.java` gains persisted, nullable fields:
- `String providerId`, `String deviceKindId`, `DeviceDescriptor capabilities` (snapshot).
- Migration: on `SaveService.load()`, any `DeviceSave` with `providerId==null` ⇒ `"pcpanel"`. Do not
  guess `deviceKindId`/`capabilities` (never stored before); back-fill + persist at next connect.
- `DeviceSave`/`Profile` constructors stop requiring `DeviceType`; they take the descriptor (or a
  `Supplier<LightingConfig>` default). Keep `DeviceType` overloads as deprecated shims during
  transition (callers: `DeviceResource.java:93`, `DeviceSave.java:69`, `Device.java:136`,
  `SaveService.java:172`, `SetMuteOverrideService.java:166`).
- This **fixes the disconnected-device gap**: with capabilities persisted, the REST/WS layer can
  render a device's config even when it's unplugged (today `getDevice`/`getLighting` require a live
  `Device`).
- The polymorphic command/knob deserializers (`CommandMapDeserializer`, `KnobSettingMapDeserializer`)
  are keyed by `Integer` and need **no change**.
- If/when `Profile.lightingConfig` becomes a polymorphic interface, register
  `@JsonTypeInfo(defaultImpl = <PCPanel RGB impl>.class)` so untagged legacy blobs still load
  (mirror the try-new-then-old pattern of `CommandMapDeserializer`). Bump the save version and reuse
  `SaveService.encounterOldVersion(...)` + `.bak` backup.

### 2.7 Event generalization

- Move `KnobRotateEvent`/`ButtonPressEvent` out of `DeviceCommunicationHandler` into
  `device.event` (or keep as records but fired by providers). Rename to `ControlInputEvent` /
  `ButtonInputEvent` is optional and high-churn — **defer** (every `@Observes` site must change
  atomically: `InputInterpreter`, `OSCService:104,120`, `MqttDeviceService:96,107`,
  `EventBroadcaster:70,74`, `DeviceHolder.triggerCommandsOf:107`). Shapes already generic; keep
  names in Phase 1, rename in a later cosmetic pass.
- `DeviceScanner.DeviceConnectedEvent(serial, DeviceType)` → `DeviceConnectedEvent(serial,
  DeviceDescriptor)`. Only consumer is `DeviceHolder.deviceAdded` (`:49`).
- `PCPanelControlEvent` keeps its name internally (it's already a lie — `knob` carries button
  indices too); generic rename deferred.

### 2.8 Frontend (descriptor-driven rendering)

- Backend sends `DeviceDescriptor` inside `DeviceDto` + `DeviceSnapshotDto` (regenerated into
  `backend.types.ts`). Unify the `deviceType` String-vs-enum inconsistency
  (`DeviceDto.deviceType: DeviceType` vs `DeviceSnapshotDto.deviceType: string`).
- New `DeviceCapabilitiesService` (Angular signals) exposes `isPro`, `knobCount`, `isSlider(idx)`,
  `controlsOf(kind)` derived from the descriptor — killing the 4 duplicated `isPro`/`idx>=5`
  definitions (`pc-device.component.ts:159`, `control.component.ts:57`, `lighting.component.ts:118`,
  `device.component.ts:53`).
- A `DeviceRendererComponent` dispatcher chooses:
  - `PcDeviceComponent` (kept, the nice PCPanel layout) when `provider==='pcpanel'`.
  - new `GenericDeviceComponent` (a grid of the already-dumb `pc-knob`/`pc-fader` + generic light
    swatches + numeric readouts for analog outputs) for everything else.
- Lighting page, per-control lighting rail, brightness slider, and logo all gate on capability flags
  (`hasGlobalLighting`, per-control `lightOutput`, `hasLogo`). Deej shows none of it.
- Move the byte-protocol leakage (`analogPct` `/255`, the logarithmic `actualPct` formula in
  `control.component.ts:107-109`, signed-byte `u8` conversions in `lighting.component.ts:160-172`)
  behind descriptor `min/max`; keep the PCPanel firmware-byte math inside the PCPanel-only
  `devices/pcpanel/` namespace.
- `command-catalog.ts` / `command-fields.component.ts` are already kind-keyed and need ~no change.
- Update `device-state.service.ts` `isWsEvent` exhaustive switch when adding
  `analog_output_changed` (it fails closed — new events silently dropped until handled).

---

## 3. Phased implementation

Each phase keeps the JVM build green (`./mvnw -q clean compile -Dquarkus.native.enabled=false`) and
the TS contract regenerated, and is independently committable. Native-image validation is batched at
the phases that add native libraries (4, 5).

### Phase 1 — Backend descriptor + provider SPI; PCPanel becomes a provider (no behavior change)
Foundation. PCPanel keeps working identically; nothing user-visible changes.
1. Add `device.descriptor` records/enums (§2.2) + `device.provider` SPI (§2.1).
2. Add `PCPanelHidProvider` that wraps the current `DeviceScanner` logic and builds the three
   built-in descriptors from `DeviceType`. Add `DeviceProviderRegistry` (injects
   `Instance<DeviceProvider>`, starts/stops providers on Quarkus start/stop).
3. Change `DeviceConnectedEvent` to carry `DeviceDescriptor`; update `DeviceHolder.deviceAdded`
   (`:49-67`) to build a device from the descriptor.
4. Collapse `DeviceFactory` (3 builders) + `PCPanel{Mini,Pro,RGB}Device` into one descriptor-driven
   `Device` (or keep subclasses but construct via descriptor). Replace the 3-way `if`
   (`DeviceHolder.java:54-62`).
5. Move analog normalization into `PCPanelHidProvider` (remove the `if PCPANEL_RGB` from
   `InputInterpreter.java:41-43`).
6. Add the descriptor to `DeviceDto`/`DeviceSnapshotDto`; **register all new types in
   `graalvm/NativeImageConfig.java`**; recompile to regenerate `backend.types.ts`.
7. Replace the 4 ad-hoc geometry re-derivations (`SetMuteOverrideService`, `OSCService`,
   `ProVisualColorsService`, `applyWorkaround`) with reads from the descriptor.

**Acceptance:** app builds (JVM), PCPanel devices connect/render/light/assign exactly as before,
existing `profiles.json` loads unchanged.

> **Implemented (2026-06-20).** Backend foundation landed: `device.descriptor.*` records/enums,
> `device.provider.{DeviceProvider,DeviceProviderRegistry}`, `DescriptorFactory` (built-in PCPanel
> descriptors), `DeviceScanner` is now the `pcpanel` provider, descriptor flows through
> `DeviceConnectedEvent` + `DeviceDto`/`DeviceSnapshotDto` (additive — old fields kept), all new
> types registered for native-image reflection (incl. element `[]` arrays), TS contract
> regenerated. JVM build green.
>
> **One intentional behavior nuance for PCPanel RGB only:** analog normalization moved from
> `InputInterpreter` to the provider edge (`DeviceCommunicationHandler.interpretInputData`), so the
> `KnobRotateEvent` on the bus now carries the canonical 0–255 value for RGB instead of the raw
> 0–100. This makes the bus consistent for all device types (a requirement for OSC/MQTT/WS to work
> with Deej/MIDI later) and **fixes** two latent RGB inconsistencies: the live WS knob value now
> matches the stored device snapshot (which already used 0–255), and OSC output for RGB now spans
> the full 0.0–1.0 (it previously maxed out at 100/255 ≈ 0.39). Side effects for RGB-panel owners:
> MQTT subscribers see 0–255 instead of 0–100, and the send-only-if-delta / twitch filters now
> operate in 0–255 space (consistent with Mini/Pro, which always did). Mini/Pro are unaffected.
> If strict no-change for RGB MQTT is required, normalize after the delta/debounce stage instead.

### Phase 2 — Persistence becomes self-identifying (§2.6)
1. Add nullable `providerId`/`deviceKindId`/`capabilities` to `DeviceSave`; load-migration +
   connect-time back-fill; save-version bump + `.bak`.
2. Decouple `DeviceSave`/`Profile` constructors from `DeviceType` (descriptor or default supplier).
3. Make REST `getDevice`/`getLighting`/profile endpoints work for disconnected devices using the
   persisted descriptor.

**Acceptance:** unplugged PCPanel still shows its config + lighting in the UI; round-trips
save/load.

### Phase 3 — Frontend descriptor-driven rendering (§2.8)
1. `DeviceCapabilitiesService`; remove duplicated `isPro`/`idx>=5`.
2. `GenericDeviceComponent` + `DeviceRendererComponent` dispatcher; PCPanel keeps `PcDeviceComponent`.
3. Gate lighting page / brightness / logo on capability flags.
4. Replace hardcoded enum lists in `debug.service.ts`, `settings.component.ts`,
   `selected-device.service.ts`, `home.component.html`.

**Acceptance:** PCPanel UI unchanged; a hand-crafted generic descriptor renders as a usable grid.

### Phase 4 — Deej provider (serial) — first real external device
1. Add `jSerialComm 2.11.4` dependency.
2. `DeejSerialProvider` (`discoveryMode = MANUAL`): list ports; user adds a device by port+baud
   (default 9600, 8-N-1). Parse `^\d{1,4}(\|\d{1,4})*\r?\n$`, split `|`, N ints 0–1023; learn slider
   count from the first valid line; dead-band per slider; normalize 0–1023→0–255; feed
   `InputConditioner`; descriptor = N SLIDER analog inputs, no buttons, no lights.
3. UI: a "Add Deej device" flow (port+baud picker) using `DiscoveryMode=MANUAL`.
4. Native-image: `--initialize-at-run-time=com.fazecast.jSerialComm` (in **both** `pom.xml` and
   `application.properties` arg lists), commit `jni-config.json` (+ reflect + resources) generated
   via `generate-native-configs.cmd` with a device connected. Verify the native runner enumerates +
   reads a port.

**Acceptance:** a Deej (or an Arduino emitting the protocol) drives per-app volume via the existing
command layer, in both JVM and native builds.

### Phase 5 — MIDI provider
1. `MidiProvider` via `javax.sound.midi` (`discoveryMode = AUTO`): enumerate
   `MidiSystem.getMidiDeviceInfo()`; CC→analog (0–127→0–255), NOTE_ON/OFF→button (vel 0
   == release); MIDI-learn recommended over static enumeration of CC numbers.
2. Output: NOTE_ON velocity for pad color, CC for LED rings → `DeviceOutputWriter.setAnalogOutput`
   (drives Phase 6's analog-output work).
3. Native-image (**highest risk** — multiple open GraalVM/Quarkus issues): per-class
   `--initialize-at-run-time` for `com.sun.media.sound.*` providers; register
   `META-INF/services/javax.sound.midi.spi.*` resources + provider classes for reflection; likely a
   custom JNI `Feature` (the tracing agent misses the native entry points). **macOS:** treat as a
   separate track — CoreMidi4J or accept no-op MIDI on macOS (consistent with macOS-no-AWT policy).
   Validate device list in the actual native runner per-platform, not just dev mode.

**Acceptance:** a MIDI controller's faders/knobs map to volume; pads act as buttons; JVM build
solid, native build validated on Windows/Linux (macOS best-effort).

### Phase 6 — Analog outputs, polymorphic lighting, external templates
1. Wire `AnalogOutputSpec` end-to-end: `Device.setAnalogOutput`, `DeviceOutputWriter`, WS
   `analog_output_changed`, UI numeric readout, optional `CommandAnalogOutput`.
2. Make `Profile.lightingConfig` polymorphic (`LightOutputConfig` interface, PCPanel RGB =
   default-impl for legacy blobs, `NoLighting`, `AnalogLedConfig`). Generalize `OutputInterpreter`
   into per-provider `DeviceOutputWriter`s and the override layer (`IOverrideColorProvider` →
   `getOverride(serial, kind, index)`).
3. **Templates:** a config-file format (JSON/YAML) describing a `DeviceDescriptor` that "extends an
   implementation" (provider). Ship templates for known Deej builds and popular MIDI controllers;
   load from `${pcpanel.root}/device-templates/` + bundled defaults.

---

## 4. New / changed Java types (summary)

New: `device.provider.{DeviceProvider, DeviceProviderRegistry, PCPanelHidProvider, DeejSerialProvider,
MidiProvider, DiscoveryMode, ManualDeviceRequest, AbstractDeviceProvider}`;
`device.descriptor.{DeviceDescriptor, AnalogInputSpec, DigitalInputSpec, LightOutputSpec,
AnalogOutputSpec, GlobalLightingSpec, AnalogKind, LightColorModel, LightGroupKind}`;
`device.io.{DeviceConnection, InputConditioner, ControlInputEvent, ButtonInputEvent}`;
`device.output.{DeviceOutputWriter, HidLightingWriter, NoOpOutputWriter, MidiOutputWriter}`.

Changed: `DeviceScanner`→absorbed into `PCPanelHidProvider`; `DeviceFactory`/`PCPanel*Device`→one
descriptor-driven `Device`; `DeviceHolder.deviceAdded`; `InputInterpreter` (drop RGB special-case);
`DeviceSave`/`Profile`/`SaveService` (persisted identity); `OutputInterpreter`→`HidLightingWriter`;
`DeviceDto`/`DeviceSnapshotDto`/`ProVisualColorsService`; `DeviceType` demoted to PCPanel-internal.

Each new persisted/serialized/polymorphic type ⇒ add to `graalvm/NativeImageConfig.java` and ensure
it's under a TS-gen-covered package.

---

## 5. Backward-compatibility checklist

- [ ] `@JsonTypeInfo(use=Id.CLASS)` ⇒ **do not move/rename** existing `Command` classes (FQCNs are in
  saved files). If unavoidable, add `@JsonTypeName`/alias or a `TypeIdResolver`.
- [ ] New `DeviceSave` fields nullable; `providerId==null ⇒ "pcpanel"` on load.
- [ ] `Profile.lightingConfig` polymorphism (Phase 6) uses `defaultImpl = <PCPanel RGB>.class` for
  untagged legacy blobs; preserve signed-`byte` semantics.
- [ ] PCPanel control-id↔index mapping stays identical (knob i→i, slider j→j+5) so existing indices
  resolve.
- [ ] Bump save version, reuse `.bak` + `encounterOldVersion`.
- [ ] `CommandConverter` legacy `String[]` import path still compiles against any touched command
  constructors.

## 6. Native-image / build checklist

- [ ] New args (`--initialize-at-run-time` for jSerialComm, `com.sun.media.sound`) added to **BOTH**
  `pom.xml` `<properties>` and `application.properties` (they diverge; CI uses
  `application.properties`, `quarkus:dev` uses the pom).
- [ ] All new reflective/serialized/polymorphic types registered in `graalvm/NativeImageConfig.java`.
- [ ] Prefer regenerating `META-INF/native-image/reachability-metadata.json` via
  `generate-native-configs.cmd` with each new device connected, over hand-editing.
- [ ] No new AWT usage on macOS (jSerialComm/MIDI are AWT-free — keep it that way).
- [ ] MIDI: SPI service resources registered; per-class run-time init; custom JNI `Feature`;
  per-platform native-runner validation (macOS `continue-on-error` in CI ⇒ won't fail the build,
  validate manually).

## 7. TypeScript generation

- Generator globs (`pom.xml`): `rest.model.**`, `commands.Commands`, `commands.command.**`,
  `wavelink.command.**`, `**.dto.**`. **`device.**` is not covered.** Either place descriptor records
  under `rest.model` / a `*.dto.*` package, or add `com.getpcpanel.device.descriptor.**` to
  `classPatterns`. Enums map `asUnion`; `@Nullable` ⇒ optional `?`.
- New WS events (`analog_output_changed`) ⇒ add to `WsEvent.java` `@JsonSubTypes` (else absent from
  the TS union) and to `device-state.service.ts` `isWsEvent`.
- Recompile (TS gen runs in `compile`) after any DTO/command/descriptor shape change; never hand-edit
  `backend.types.ts`.

## 8. Risks & open questions

- **MIDI in native image** is the biggest unknown (open GraalVM/Quarkus issues). Prototype in
  `quarkus:dev` first; budget a custom JNI `Feature`; have a no-op fallback.
- **MIDI control identity** is device-specific — prefer a MIDI-learn UX over enumerating CC numbers.
- **Polymorphic lighting** (Phase 6) is the largest single change (touches OutputInterpreter,
  ProVisualColorsService, all color DTOs, the whole lighting UI). Kept last on purpose.
- **Event renames** (`PCPanelControlEvent`→`ControlEvent`) are cosmetic but wide; do as an isolated
  pass, not mixed into behavioral phases.
- Open: should manual (Deej) devices be keyed by port path (changes across reboots on Windows) or by
  a user-assigned id? Lean toward a stable user-assigned id stored in `DeviceSave`.

## 9. Appendix — exhaustive PCPanel-coupling file:line index

Device/HID: `device/DeviceType.java:13-15`; `DeviceFactory.java:25-35`; `DeviceHolder.java:54-62`;
`DeviceScanner.java:235-241` (VID/PID), `:131-133,168-170` (mac msgs), `:88` (thread name);
`DeviceCommunicationHandler.java:33-34,44,189-213` (proto), `:230-232` (geometry),
`:67-68` (thread names); `InputInterpreter.java:41-43` (RGB remap); `DialValueCalculator.java:42`
(0,255); `device/PCPanel{Mini,Pro,RGB}Device.java`.
Lighting: `OutputInterpreter.java:25-46,75-124,238-258`; `LightingConfig.java:49-91`;
`profile/dto/Single*LightingConfig.java`; `util/coloroverride/IOverrideColorProvider.java:11-17`;
`rest/ProVisualColorsService.java:23-25,31`.
Commands: `commands/command/CommandBrightness.java:26-32`; `commands/SetMuteOverrideService.java:207-254`.
Persistence: `profile/DeviceSave.java:23-35` (`"pcpanel"+i`); `profile/Profile.java:32-35`;
`profile/Save.java:69-71`.
REST/DTO: `rest/model/dto/DeviceDto.java:24-32`; `DeviceSnapshotDto.java:51-66`;
`rest/model/ws/Ws{Lighting,VisualColors,ProfileSwitched}*Event.java` (color buckets).
OSC: `osc/OSCService.java:111,115,129`. MQTT: `mqtt/MqttDeviceService.java:169`.
Build: `pom.xml` (TS-gen globs; two-copy native args); `application.properties` (native args);
`graalvm/NativeImageConfig.java` (reflection registry).
Frontend: `devices/visual/pc-device.component.ts:15,159,184,204,207,209`; `devices/visual/pc-logo.component.ts`;
`devices/visual/device-visual.util.ts:7-9`; `devices/pcpanel/lighting-animation.ts`;
`pages/control/control.component.ts:57-60,107-109`; `pages/lighting/lighting.component.ts:104-172`;
`pages/device/device.component.ts:53-56`; `services/{debug,selected-device}.service.ts`;
`pages/settings/settings.component.ts:39-44`; `models/generated/backend.types.ts:607` (DeviceType union).

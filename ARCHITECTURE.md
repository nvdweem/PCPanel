# PCPanel — architecture & build reference

The technical reference for this repository: what the app is, how it is built and run, how the
code is organized, and the GraalVM-native-image and native-C++ constraints. Contributor workflow
(IDE setup, PR process, conventions) lives in [CONTRIBUTING.md](CONTRIBUTING.md); agent-specific
instructions live in [CLAUDE.md](CLAUDE.md).

## What this is

Third-party/community controller software for [PCPanel](https://getpcpanel.com) USB audio-control
devices (knobs, sliders, buttons with RGB). It is a **desktop application** built as a
**Quarkus** backend (Java 25) serving an **Angular** frontend in a local browser. The app talks
to PCPanel hardware over USB HID and controls OS audio (per-process/device volume, mute, default
device) plus integrations (OBS, Voicemeeter, Elgato Wave Link, Discord, Home Assistant, OSC, MQTT).

Development focus is Windows; Linux is best-effort. The project was migrated from Spring
Boot + JavaFX to Quarkus + Angular, and ships as a **GraalVM native image** (see git history /
`copilot/migration-to-quarkus-again` branch context).

## Build & run

The toolchain is the Maven wrapper (`./mvnw` / `mvnw.cmd`). Java 25 is required (GraalVM CE 25 for
native builds).

```bash
./mvnw quarkus:dev          # dev mode: backend on :7654, Quinoa runs Angular dev server on :4200 with live reload
./mvnw clean package        # builds a NATIVE image by default (quarkus.native.enabled=true in pom)
./mvnw clean package -Dquarkus.native.enabled=false   # JVM-only jar, much faster, no GraalVM needed
./mvnw test                 # unit tests (surefire)
./mvnw test -Dtest=ClassName#method   # single test
./mvnw verify -Pnative      # native build + failsafe integration tests against the runner binary
```

- `package` produces a native executable at `target/*-runner` (Linux) / `target/*-runner.exe` (Windows).
- CI (`.github/workflows/build-and-release.yml`) builds the native image on Windows via
  `mvn -B verify -Pnative` (so the failsafe integration tests run against the runner binary) and on
  Linux and macOS via `mvn -B package -Pnative`, wraps it in installers (Windows Inno Setup `.exe`,
  Linux `.deb` / AppImage / Flatpak — see `packaging/`), and publishes a per-branch pre-release. The
  native image is
  NOT self-contained: it loads companion `*.dll`/`*.so` libraries from its own directory, so every
  artifact must bundle them alongside the executable. The Linux artifacts also bundle **`kdotool`**
  (Apache-2.0) next to the executable — it resolves the focused window on KDE Plasma (Wayland and X11)
  for focus volume. `packaging/linux/fetch-kdotool.sh` pins the version + sha256 and is cache-keyed in
  CI on its own hash (download once per pin). `LinuxProcessHelper` prefers a `kdotool` sibling of its
  own binary over the `PATH` lookup; `xdotool` is only an optional non-KDE-X11 fallback (kdotool covers
  X11, so the two are never both required). Inside the Flatpak, kdotool runs in the sandbox and drives
  the host KWin over D-Bus (`--talk-name=org.kde.KWin`); `kdotool-wrapper.sh` points its `TMPDIR` at the
  host-visible per-app cache dir (`XDG_CACHE_HOME` = `~/.var/app/<id>/cache`, identity-mapped into the
  sandbox — *not* `$HOME`, which is an unbacked overlay) so the host KWin can read the temp KWin script
  kdotool generates.
- **Releasing:** `<project.baseversion>` in `pom.xml` is the version source of truth (artifacts are
  `<baseversion>.<build>`). Bump it with `packaging/bump-version.sh <version>` (also updates the
  AppStream metadata), then push a `releases/<version>` branch to trigger a pre-release build. CI bakes
  the same `<baseversion>.<build>` into the app via `-Dquarkus.application.version=` on `mvn package`, so
  the UI footer reports the build number for official builds (local/dev stays at `<project.version>`,
  i.e. `-SNAPSHOT`).
- **Run two instances side by side:** pass the `skipfilecheck` arg (otherwise launching a second
  instance just focuses the already-installed one — see `Main`/`FileChecker`). For a separate dev
  data dir, set `pcpanel.root=${user.home}/.pcpaneldev/` (dev profile already does this).

### Frontend (`src/main/webui`, Angular 21)

Managed by the Quinoa Quarkus extension — normally you don't run it directly; `quarkus:dev` proxies it.
Standalone: `cd src/main/webui && npm install && npm start` (serves :4200, proxies `/api` + `/ws` to :7654).

**TypeScript types are generated from Java**, not hand-written. The `typescript-generator-maven-plugin`
(runs in the `compile` phase) writes `src/main/webui/src/app/models/generated/backend.types.ts` from
these classPatterns (the authoritative list is in `pom.xml`):

- `com.getpcpanel.rest.model.**` — the REST/WebSocket DTO contract
- `com.getpcpanel.commands.Commands` — the command-list container
- `com.getpcpanel.**.command.**` — every feature's command classes (each feature owns a `command/`
  subpackage; the glob picks up new integrations without a pom edit)
- `com.getpcpanel.device.descriptor.**` — the device-descriptor model
- `**.dto.**` — any DTO package

When you change a DTO or
command shape, recompile so the frontend contract regenerates — don't edit the generated file.

## Architecture

Quarkus CDI (Arc) app. Entry point is `com.getpcpanel.Main` (`@QuarkusMain`); beans are wired by
injection, and cross-cutting communication uses the **CDI event bus** (`jakarta.enterprise.event.Event`
fire + `@Observes`) heavily rather than direct calls. `docs/events.md` catalogs the events with their
firers and observers — keep it current when you add or remove an event.

**Hardware path (`device/`):** the device layer is the hardware-abstraction layer (HAL) and is **not**
an integration — it provides no commands. `DeviceScanner` (in `device/provider/pcpanel/`) discovers HID
devices via hid4java; `DeviceCommunicationHandler` (one per device, own thread + queue, same package)
reads knob/button input and writes RGB/output. `Device` subclasses (`PCPanelMini/Pro/RGB`, in `device/`)
model each hardware variant; `DeviceHolder` (in `device/`) is the cross-provider registry. Physical input
becomes a `PCPanelControlEvent` / `ButtonClickEvent` on the event bus.

**Device providers (`device/provider/`, `device/descriptor/`):** the device layer is generalized so
PCPanel is one `DeviceProvider` among several — providers are `@ApplicationScoped` beans discovered via
`Instance<DeviceProvider>` (NOT build-time stereotypes; every build contains all of them).
`DeviceScanner` is the `"pcpanel"` HID provider (`device/provider/pcpanel/`); `DeejSerialProvider`
(serial, jSerialComm, `device/provider/deej/`) and
`MidiProvider` (`javax.sound.midi`, `device/provider/midi/`) are external providers; each provider
absorbs its own IO transport (e.g. `SerialTransport`/`JSerialComm*` under `deej/`). A device is described by a data
`DeviceDescriptor` (analog/digital inputs with source ranges, light/analog outputs, capabilities)
rather than the `DeviceType` enum, which is now PCPanel-provider-internal. Each provider normalizes
its raw analog values to the canonical **0–255** internal domain at its edge (PCPanel RGB 0–100, Deej
0–1023, MIDI 0–127 → 0–255), so `DialValueCalculator`/`KnobSetting`/commands are untouched. Non-PCPanel
devices use a lightless `GenericDevice` — `Device.deviceType()` is nullable, so guard PCPanel/HID-only
paths (lighting, `OutputInterpreter.sendInit`) against `deviceType() == null`. `DeviceSave` persists
`providerId`/`deviceKindId`/`capabilities` (back-filled at connect; legacy saves default to
`pcpanel`). The Angular UI renders any device from its descriptor (`DeviceRendererComponent` →
`PcDeviceComponent` for PCPanel, else `GenericDeviceComponent`). Full design + per-phase status:
`docs/device-layer-generalization-plan.md`.

**Command model (`commands/` = engine; `integration/*/command/` = the commands):** A user's
per-dial/button configuration is a `Commands` (list of `Command` subclasses). `commands/` holds only the
engine — `Command`, the `Dial/Button/DeviceAction` SPIs, `CommandDispatcher`, `DialValue`, the
`@CommandMeta`/`CommandModule` registry. Each concrete command lives in its feature's package, e.g.
`integration.volume.command.CommandVolumeProcess`, `integration.keyboard.command.CommandKeystroke`/
`CommandMedia`, `integration.obs.command.CommandObs`. Commands are JSON-polymorphic:
`@JsonTypeInfo(use = Id.NAME)` on `Command` with `@JsonTypeName` ids per subclass, registered as an
explicit allowlist through the `CommandModule` SPI (`CommandSubtypeRegistrar` collects every module's
subtypes into the ObjectMapper). Type ids are names, not class names, so a command's package location
is irrelevant to (de)serialization — see `docs/feature-module-structure.md`. Commands are part of the
generated TS contract. **A package is an `integration` only if it provides commands** — providers/HAL/
infra are not.

**Native audio abstraction (`integration/volume/platform/`):** `ISndCtrl` is the OS-audio facade
(volume/mute/default device, focus app) — it is the backend the volume commands drive, so it lives in
the volume feature. Implementations are selected at **build time** by platform stereotypes:
`@WindowsBuild` (`SndCtrlWindows` → JNI to `SndCtrl.dll` via `SndCtrlNative`, both in
`integration/volume/platform/windows/`; C++ source in `src/main/cpp/`) and `@LinuxBuild`
(`SndCtrlPulseAudio` in `platform/linux/`, via JNA/PulseAudio). These stereotypes wrap
Quarkus `@IfBuildProperty(name="pcpanel.build.os", ...)` keyed off `pcpanel.build.os` (set at build
time from `os.detected.name`), so **a given build only contains one platform's beans** — guard
optional platform beans with `Instance<T>` injection, and use `CdiHelper` to fetch beans from
non-CDI code. On Linux many native calls degrade to a no-op `ISndCtrl`.

**Persistence (`profile/`):** All user config (`Save` → devices/`Profile`s/command maps) is a single
JSON file at `${pcpanel.root}/profiles.json` managed by `SaveService`. Custom Jackson deserializers
(`CommandMapDeserializer`, `KnobSettingMapDeserializer`) handle the polymorphic command maps. The
data dir is `~/.pcpanel` on Windows/macOS; on Linux it is resolved by `util/PcPanelRoot` to honor
`$PCPANEL_ROOT`, then a pre-existing legacy `~/.pcpanel`, then `$XDG_CONFIG_HOME/pcpanel`
(`~/.config/pcpanel`) — this is what makes the Flatpak (sandbox `$HOME` → `~/.var/app/<id>/config`)
and immutable distros persist settings without a host grant. `PcPanelRoot.resolve()` is the single
source of truth: `Main` publishes it as the `pcpanel.root` system property for the native image, and
the non-CDI `FileChecker`/`HidDebug` call it directly. See `linux.md` for the user-facing details.

**Frontend bridge (`rest/`):** the **shared** JAX-RS + websocket bridge: `SettingsResource`,
`PlatformResource`, `SystemResource`, `IconResource`/`ProcessResource` (the app/process picker, shared
across features), `EventWebSocket` at `/ws/events`, `EventBroadcaster`, `LocalHttpGuard`, and the
`rest/model/` DTO+WS contract. Feature-specific resources live with their feature instead:
device-management REST in `device/rest/` (`DeviceResource`, `SerialResource`, `MidiResource`),
volume/overlay REST in `integration/volume/`, each external connector's REST in `integration/<name>/rest/`.
The backend pushes device/state snapshots to the Angular UI over the socket. There is no separate window
framework — the "UI" is the browser served by Quinoa. `StaticCacheControl` (another `@Observes Router`
filter) stamps `no-cache` on every UI path except the content-hashed bundle files — Quarkus's static
default (`Cache-Control: immutable, max-age=86400`) otherwise keeps a browser on the previous release's
frontend for up to a day after an app update, without revalidating even on reload (see issue #113).
Production builds ship the frontend source maps (`sourceMap` in `angular.json`'s production config) so
user-reported console errors carry readable TS stack traces.

**Web-exposure security model:** the API is unauthenticated, so it must stay reachable only from the
local machine. Two layers enforce this: `quarkus.http.host=127.0.0.1` keeps other hosts off, and
`LocalHttpGuard` (a `@Observes Router` Vert.x filter, lowest order) rejects any request whose `Host`
or `Origin` header is not loopback — defeating DNS rebinding and cross-site WebSocket hijacking from a
website the user visits. `EventWebSocket.onOpen` re-checks the handshake with the same
`LocalHttpGuard` helpers as a second layer. Loopback `Origin` is accepted on any port (so dev's
`:4200` Quinoa proxy works); absent `Origin` is allowed (non-browser clients) with the `Host` check as
the backstop. Toggle with `pcpanel.http.local-only` (default true). This does **not** authenticate
*local* callers — defending against other processes on the same machine would need a token and is out
of scope. See also [SECURITY.md](SECURITY.md).

**Integrations (`integration/*` — command-providing features only):** the external connectors
`integration/obs/` (OBS websocket), `voicemeeter/` (JNA), `wavelink/` + `dev/niels/wavelink/` (Elgato
Wave Link RPC client), `osc/`, `mqtt/` (Eclipse Paho mqttv5), `homeassistant/`, `discord/`; plus the
feature families `volume/`, `keyboard/`, `program/`, `analogbands/`, `profile/`, and `device/` (the
brightness command only). Each owns its `command/` + `CommandModule` and (where applicable) its REST,
SPI impls, and service. End-user setup per integration: `docs/integrations.md`. The on-screen volume
overlay lives in `integration/volume/overlay/`: a Win32 JNA
layered window on Windows (`Win32VolumeOverlay`) and a
desktop-drawn OSD over D-Bus on Linux/Wayland (`LinuxOverlay`, AWT-free) — KDE Plasma's native volume
OSD (`org.kde.osdService.volumeChanged`, the same real-time bar as Plasma's own volume keys) when
plasmashell is on the bus, **else no overlay** (clean no-op). A notification fallback was deliberately
rejected: the freedesktop notification protocol can't guarantee in-place replacement across daemons, so
it risks spamming one notification per knob tick — worse than nothing. The desktop owns placement/styling,
so those `Save` settings don't apply on Linux (the settings UI greys them out). macOS stays a no-op.
Selection is the runtime `Platform` check in `Overlay.createOverlay()`.
`util/tray/` is the system tray (Wayland uses the D-Bus StatusNotifierItem protocol via dbus-java).

`integration/homeassistant/` is *outbound* control (the app drives Home Assistant), distinct from the
MQTT auto-discovery in `integration/mqtt/` (which lets Home Assistant discover the app). It holds both
its own command types (`integration/homeassistant/command/` — every feature's commands live in its own
`command/` package, all picked up by the single `com.getpcpanel.**.command.**` typescript-generator
classPattern in `pom.xml`) and a minimal REST client
(`HomeAssistantClient`, JDK `HttpClient`, no extra dependency). Multiple servers are configured in
settings (`Save.homeAssistantServers`); a command with a blank server id auto-resolves to the only
configured server (the UI also auto-selects it). **Actions are authored as pasted HA "action" YAML**
(the format HA's Developer Tools → Actions page produces) rather than hand-built pickers — the UI
links out to that page on the configured server. `HaActionYaml` (snakeyaml, `SafeConstructor`) parses
the YAML into the `domain.service` + flat body the REST API wants. The dial command additionally maps
the 0..1 dial position to a number via min/max or an `exp4j` formula (variable `x`) and substitutes it
for the `{{ value }}` token in the YAML before sending.

## GraalVM native image — important

Native image config is the most fragile part of the build, and it lives in **two** places that
must be kept in sync: the `quarkus.native.*` properties in **`pom.xml`'s `<properties>` block** and
the copy in **`application.properties`**. Which one wins depends on how the build is invoked: a full
`mvn package`/`mvn verify` (CI) runs `process-resources`, which filters `application.properties` onto
the classpath where its `additional-build-args` **outranks** the pom property — so for CI,
`application.properties` is authoritative. When `quarkus:build`/`quarkus:dev` is invoked directly,
`process-resources` has not run, so the pom value is used. **Change both**, or you will get different
native images locally vs. in CI — `NativeBuildArgsParityTest` enforces this and fails the build if the
two `additional-build-args` lists drift apart (the per-OS `${native.awt.args}`/
`${native.platform.linker.args}` placeholders are ignored since they are identical references in both).
The OS-specific AWT init policy is shared between them via the
Maven-filtered `${native.awt.args}` placeholder (default vs. the `os-mac` profile in `pom.xml`);
`src/main/resources` has `<filtering>true</filtering>`, which is what makes that substitution work.
Key constraints baked into those args, change with care:

- `-J-XX:-UseCompressedOops` is required so `Unsafe.arrayIndexScale` matches the runtime (8-byte
  refs); omitting it segfaults jctools.
- JNA, hid4java, jnativehook, dbus, AWT-dependent, and Voicemeeter classes are
  `--initialize-at-run-time`; certain AWT font/hint classes are `--initialize-at-build-time`.
- **macOS has no `libawt` in the native image at all** (GraalVM/Quarkus reject AWT there:
  `quarkus-awt` is dropped via the `os-non-mac` profile, and the `os-mac` profile defers the whole
  AWT/Java2D/Swing/ImageIO subsystem to run-time). So macOS must never *call* AWT: the overlay is a
  no-op (`NoOpOverlayWindow`), icons are disabled, keystrokes use CoreGraphics `CGEvent`
  (`com.getpcpanel.integration.keyboard.platform.osx.OsxKeyboard`, the macOS `Keyboard` impl), the tray
  and `java.awt.Desktop` are skipped. JNA classes
  that run `Native.load` in their initializer must be `--initialize-at-run-time` (narrowly, by class —
  a package-wide directive would wrongly catch Quarkus's build-time CDI `_Bean` objects).
- Windows-only GUI-subsystem linker flags (`/SUBSYSTEM:WINDOWS`, `/ENTRY:mainCRTStartup`) are MSVC
  flags injected only via the `os-windows` profile (`native.platform.linker.args`); they break
  GNU ld / ld64, so never add them to the shared block.
- Reachability metadata lives under `src/main/resources/META-INF/native-image/`. Regenerate it with
  the tracing agent via the `native-config-gen` Maven profile
  (`mvn -Pnative-config-gen test -Dquarkus.native.enabled=false`) or, on Windows,
  `generate-native-configs.cmd`.
- A REST DTO returning `List<SomeRecordDto>` needs **both** the element record and its array type
  (`SomeRecordDto[]`) registered for reflection — Jackson reflectively instantiates the array per
  `List` during serialization. The tracing agent only records what it observes, so an endpoint traced
  with an empty list silently omits these and then throws `MissingReflectionRegistrationError` → HTTP
  500 at runtime once the list is non-empty (works fine in JVM/dev, which always has reflection).
  Don't rely on tracing for this: register them explicitly on the response DTO with
  `@RegisterForReflection(targets = { Foo.class, Foo[].class, ... })`, listing every nested element
  record and its `[].class` form (see `rest/wavelink/dto/WaveLinkResponseDto`).
- A serialised field of a JDK type Jackson handles via a **built-in `StdSerializer`** needs that
  serializer class registered too — e.g. a `java.io.File` field uses
  `com.fasterxml.jackson.databind.ser.std.FileSerializer`, whose no-arg ctor must be reflectively
  instantiable or the endpoint 500s with "FileSerializer has no default constructor" once the value is
  non-null (`/api/audio/applications` → `ISndCtrl.RunningApplication.file`; works in JVM/dev). Register
  the serializer by name in `NativeImageConfig.classNames`. The coverage tests don't catch this (it is
  Jackson-internal, not a project type), so the reliable check is to **run the native binary and curl the
  list/DTO REST endpoints** — JVM/dev mode never reproduces it.
- **Discovery guards catch the above automatically — keep them green.** `ReflectionRegistrationCoverageTest`
  walks the Jackson `Command` hierarchy's serialised property graph and fails if any concrete subtype, or
  any concrete project record/class it reaches (plus the `Foo[]` array form for `List`/`Set` of a concrete
  element), is missing from the `@RegisterForReflection`/reachability-metadata registrations — so a new
  command or a record nested in one can't ship unregistered. `ProxyRegistrationCoverageTest` does the same
  for JNA `Library` proxies. Both run on every OS/JVM build, so the platform that forgot a registration
  need not be the one running the test. When one fails, add the named type to `NativeImageConfig`.
- **jSerialComm (the Deej serial provider) is pinned to 2.10.2** — the last release before it bundled
  an Android USB-serial driver (2.10.3+), whose `android.*` references fail the native build under
  `--link-at-build-time` (we never run on Android). It also can't self-extract its bundled native lib
  in a native image (it locates the jar via `CodeSource`, null there), so the Windows lib is placed
  next to the runner exe via `maven-dependency-plugin` (os-windows profile) and loaded from
  `java.library.path` — the same companion-DLL model as `SndCtrl.dll`. Linux/macOS would need their
  `.so`/`.jnilib` next to the binary too.
- **`javax.sound.midi` (the MIDI provider) does not enumerate devices in the native image** (known
  GraalVM limitation): the image links and startup is safe — every `MidiSystem` call is
  `Throwable`-guarded so a missing MIDI subsystem can't crash startup or affect PCPanel/Deej — but
  `getMidiDeviceInfo()` returns empty in native. MIDI input works in JVM/dev mode only until a custom
  JNI `Feature` (and CoreMidi4J on macOS) is added. `com.sun.media.sound` and
  `javax.sound.midi.MidiSystem` are `--initialize-at-run-time`.

## Native C++ (`src/main/cpp/`, Windows DLL)

`SndCtrl.dll` (audio control via Windows Core Audio). The built DLL is committed at
`src/main/resources/SndCtrl.dll`; the Maven/CI build only bundles it, it does not rebuild it.

The sources are compiler-portable and build via **CMake** (`src/main/cpp/CMakeLists.txt`) with
either MSVC or MinGW-w64 — including a **cross-compile from Linux** (no Visual Studio needed). See
`src/main/cpp/README.md` for the full instructions; in short:
`apt install g++-mingw-w64-x86-64 cmake`, then `cmake -B build -S src/main/cpp
-DCMAKE_TOOLCHAIN_FILE=$PWD/src/main/cpp/mingw-w64-x86_64.toolchain.cmake -DWIN_JDK_HOME=<windows-jdk>
&& cmake --build build`. It needs the **Windows** JNI headers (unzip any Windows JDK), not a Linux
JDK's (whose `jni_md.h` would make `jlong` 32-bit and skip the `__declspec(dllexport)` exports).

What used to make it Visual-Studio-only and how it was removed: ATL `CComPtr`/`CComQIPtr` →
portable `comptr_compat.h` shim; `_bstr_t`/`<comdef.h>` → `WideCharToMultiByte`; `__uuidof` on the
custom COM interfaces → explicit `__CRT_UUID_DECL` (guarded by `__MINGW32__`); MSVC's implicit
transitive includes → explicit ones in `pch.h`. All MSVC-path behaviour is preserved (changes are
`#ifdef`-guarded or behaviour-identical). The MinGW DLL statically links libstdc++/libgcc/winpthread
so it has no extra runtime-DLL dependencies (it is ~1 MB stripped vs MSVC's ~70 KB — expected).

The legacy Visual Studio solution (`SndCtrl.sln`/`.vcxproj`) and the `SndCtrlTest` harness (JNI
access violations otherwise silently close the app) are still present and still work; the one
hardcoded VS setting is the JNI include dir under project properties → C/C++ → General → Additional
Include Directories. **There is no automated test for the DLL** — final verification needs the app
running on Windows against PCPanel hardware.

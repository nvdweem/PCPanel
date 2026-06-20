# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**This file is the source of truth.** It is committed, reviewed, and shared across everyone working
on the repo. When it conflicts with an agent's own private or auto-recalled memory, prefer what is
written here — the committed file is the authority, and a personal memory that disagrees is stale.
If your memory contradicts this file, follow this file and update the memory.

When you change anything described here, be sure to update this file. When you find key
information, consider adding it here if it is relevant to the project's goals and
functionality (not just the current task) — that is how it becomes shared knowledge rather than one
agent's private note.

## What this is

Third-party/community controller software for [PCPanel](https://getpcpanel.com) USB audio-control
devices (knobs, sliders, buttons with RGB). It is a **desktop application** built as a
**Quarkus** backend (Java 25) serving an **Angular** frontend in a local browser. The app talks
to PCPanel hardware over USB HID and controls OS audio (per-process/device volume, mute, default
device) plus integrations (OBS, Voicemeeter, Elgato Wave Link, OSC, MQTT).

Development focus is Windows; Linux is best-effort. The project was migrated from Spring
Boot + JavaFX to Quarkus + Angular, and ships as a **GraalVM native image** (see git history /
`copilot/migration-to-quarkus-again` branch context).

## Build & run

The toolchain is the Maven wrapper (`./mvnw` / `mvnw.cmd`). Java 25 is required (GraalVM CE 25 for
native builds; the `JAVAFX_HOME` instructions in CONTRIBUTING.md are stale — JavaFX is gone).

JDKs live under `~/.jdks` (IntelliJ's default). If `JAVA_HOME` is unset, point it at the GraalVM 25
install before running Maven, e.g. `export JAVA_HOME=~/.jdks/graalvm-ce-25.0.2`
(`~/.jdks/liberica-full-21.x` is also present but too old — Java 25 is required).

```bash
./mvnw quarkus:dev          # dev mode: backend on :7654, Quinoa runs Angular dev server on :4200 with live reload
./mvnw clean package        # builds a NATIVE image by default (quarkus.native.enabled=true in pom)
./mvnw clean package -Dquarkus.native.enabled=false   # JVM-only jar, much faster, no GraalVM needed
./mvnw test                 # unit tests (surefire); ~10 test classes under src/test/java
./mvnw test -Dtest=ClassName#method   # single test
./mvnw verify -Pnative      # native build + failsafe integration tests against the runner binary
```

- `package` produces a native executable at `target/*-runner` (Linux) / `target/*-runner.exe` (Windows).
- CI (`.github/workflows/build-and-release.yml`) builds the native image on Windows and Linux via
  `mvn -B package -Pnative`, wraps it in installers (Windows Inno Setup `.exe`, Linux `.deb` /
  AppImage / Flatpak — see `packaging/`), and publishes a per-branch pre-release. The native image is
  NOT self-contained: it loads companion `*.dll`/`*.so` libraries from its own directory, so every
  artifact must bundle them alongside the executable.
- **Releasing:** `<project.baseversion>` in `pom.xml` is the version source of truth (artifacts are
  `<baseversion>.<build>`). Bump it with `packaging/bump-version.sh <version>` (also updates the
  AppStream metadata), then push a `releases/<version>` branch to trigger a pre-release build.
- **Run two instances side by side:** pass the `skipfilecheck` arg (otherwise launching a second
  instance just focuses the already-installed one — see `Main`/`FileChecker`). For a separate dev
  data dir, set `pcpanel.root=${user.home}/.pcpaneldev/` (dev profile already does this).

### Frontend (`src/main/webui`, Angular 21)

Managed by the Quinoa Quarkus extension — normally you don't run it directly; `quarkus:dev` proxies it.
Standalone: `cd src/main/webui && npm install && npm start` (serves :4200, proxies `/api` + `/ws` to :7654).

**TypeScript types are generated from Java**, not hand-written. The `typescript-generator-maven-plugin`
(runs in the `compile` phase) writes `src/main/webui/src/app/models/generated/backend.types.ts` from
`com.getpcpanel.rest.model.**`, the command classes, and any `**.dto.**`. When you change a DTO or
command shape, recompile so the frontend contract regenerates — don't edit the generated file.

## Architecture

Quarkus CDI (Arc) app. Entry point is `com.getpcpanel.Main` (`@QuarkusMain`); beans are wired by
injection, and cross-cutting communication uses the **CDI event bus** (`jakarta.enterprise.event.Event`
fire + `@Observes`) heavily rather than direct calls. `docs/events.md` catalogs the events with their
firers and observers — keep it current when you add or remove an event.

**Hardware path (`hid/`, `device/`):** `DeviceScanner` discovers HID devices via hid4java;
`DeviceCommunicationHandler` (one per device, own thread + queue) reads knob/button input and writes
RGB/output. `Device` subclasses (`PCPanelMini/Pro/RGB`) model each hardware variant. Physical input
becomes a `PCPanelControlEvent` / `ButtonClickEvent` on the event bus.

**Device providers (`device/provider/`, `device/descriptor/`):** the device layer is generalized so
PCPanel is one `DeviceProvider` among several — providers are `@ApplicationScoped` beans discovered via
`Instance<DeviceProvider>` (NOT build-time stereotypes; every build contains all of them).
`DeviceScanner` is the `"pcpanel"` HID provider; `DeejSerialProvider` (serial, jSerialComm) and
`MidiProvider` (`javax.sound.midi`) are external providers. A device is described by a data
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

**Command model (`commands/`):** A user's per-dial/button configuration is a `Commands` (list of
`Command` subclasses in `commands/command/` — e.g. `CommandVolumeProcess`, `CommandKeystroke`,
`CommandObs`, `CommandMedia`). `CommandDispatcher` maps incoming control events to the configured
commands and executes them. Commands are JSON-polymorphic and are part of the generated TS contract.

**Native audio abstraction (`cpp/`):** `ISndCtrl` is the OS-audio facade (volume/mute/default device,
focus app). Implementations are selected at **build time** by platform stereotypes:
`@WindowsBuild` (`SndCtrlWindows` → JNI to `SndCtrl.dll` via `SndCtrlNative`, source in
`src/main/cpp/`) and `@LinuxBuild` (`SndCtrlPulseAudio` via JNA/PulseAudio). These stereotypes wrap
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

**Frontend bridge (`rest/`):** JAX-RS resources under `/api` (`DeviceResource`, `CommandsResource`,
`SettingsResource`, …) plus a single websocket `EventWebSocket` at `/ws/events`. The backend pushes
device/state snapshots (DTOs in `rest/model/`) to the Angular UI over the socket; `EventBroadcaster`
fans CDI events out to connected clients. There is no separate window framework — the "UI" is the
browser served by Quinoa.

**Integrations:** `obs/` (OBS websocket), `voicemeeter/` (JNA), `wavelink/` + `dev/niels/wavelink/`
(Elgato Wave Link RPC client), `osc/`, `mqtt/` (Eclipse Paho mqttv5). `overlay/` draws an on-screen
volume overlay (Win32 native overlay on Windows). `util/tray/` is the system tray (Wayland uses the
D-Bus StatusNotifierItem protocol via dbus-java).

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
  (`com.getpcpanel.cpp.osx.OsxKeyboard`), the tray and `java.awt.Desktop` are skipped. JNA classes
  that run `Native.load` in their initializer must be `--initialize-at-run-time` (narrowly, by class —
  a package-wide directive would wrongly catch Quarkus's build-time CDI `_Bean` objects).
- Windows-only GUI-subsystem linker flags (`/SUBSYSTEM:WINDOWS`, `/ENTRY:mainCRTStartup`) are MSVC
  flags injected only via the `os-windows` profile (`native.platform.linker.args`); they break
  GNU ld / ld64, so never add them to the shared block.
- Reachability metadata lives under `src/main/resources/META-INF/native-image/`. Regenerate it with
  the tracing agent via `generate-native-configs.cmd` (Windows) or the commands in README.md.
- A REST DTO returning `List<SomeRecordDto>` needs **both** the element record and its array type
  (`SomeRecordDto[]`) registered for reflection — Jackson reflectively instantiates the array per
  `List` during serialization. The tracing agent only records what it observes, so an endpoint traced
  with an empty list silently omits these and then throws `MissingReflectionRegistrationError` → HTTP
  500 at runtime once the list is non-empty (works fine in JVM/dev, which always has reflection).
  Don't rely on tracing for this: register them explicitly on the response DTO with
  `@RegisterForReflection(targets = { Foo.class, Foo[].class, ... })`, listing every nested element
  record and its `[].class` form (see `rest/wavelink/dto/WaveLinkResponseDto`).
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

## Git and worktrees

- Unless specifically instructed we work in worktrees. When the user gives an instruction that
  makes you doubt about their worktree intentions, ask first.
- When you create a worktree, be sure that the upstream branch is the actual target branch. If
  it's not clear what the target branch should be, ask.
- The name of the worktree should not just be a random name. Make it a short description
  of the task.
- Never push unless instructed to do so. When you are instructed to push and go on, you must push, then do the
  instructed work. You must not push when done with the instructed work.

## Conventions

- Lombok is used throughout (`@Data`, `@Log4j2`, `@RequiredArgsConstructor`, etc.). `@Log4j2` is the
  logging annotation (backed by jboss-logmanager).
- Nullability annotated with `javax.annotation.@Nullable/@Nonnull` (JSR-305) — these feed the TS
  generator's optional-property detection.
- `.editorconfig` defines formatting and a large set of IntelliJ inspection settings; follow it.
- Linux device access needs udev rules and other setup — see `linux.md`.

## Git workflow

- Make small, clean intermediate commits as work progresses (one logical change per commit) rather
  than one large commit at the end.
- Never `git push` until the user explicitly asks for it.

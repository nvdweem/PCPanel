# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

```bash
./mvnw quarkus:dev          # dev mode: backend on :7654, Quinoa runs Angular dev server on :4200 with live reload
./mvnw clean package        # builds a NATIVE image by default (quarkus.native.enabled=true in pom)
./mvnw clean package -Dquarkus.native.enabled=false   # JVM-only jar, much faster, no GraalVM needed
./mvnw test                 # unit tests (surefire); ~10 test classes under src/test/java
./mvnw test -Dtest=ClassName#method   # single test
./mvnw verify -Pnative      # native build + failsafe integration tests against the runner binary
```

- `package` produces a native executable at `target/*-runner` (Linux) / `target/*-runner.exe` (Windows).
- CI (`.github/workflows/maven-build-installer-windows.yml`) builds native images on both Windows and
  Linux via `mvn -B package -Pnative` and publishes a pre-release.
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
fire + `@Observes`) heavily rather than direct calls.

**Hardware path (`hid/`, `device/`):** `DeviceScanner` discovers HID devices via hid4java;
`DeviceCommunicationHandler` (one per device, own thread + queue) reads knob/button input and writes
RGB/output. `Device` subclasses (`PCPanelMini/Pro/RGB`) model each hardware variant. Physical input
becomes a `PCPanelControlEvent` / `ButtonClickEvent` on the event bus.

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
(`CommandMapDeserializer`, `KnobSettingMapDeserializer`) handle the polymorphic command maps.

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

Native image config is the most fragile part of the build. The **authoritative**
`quarkus.native.*` settings (resource includes, `additional-build-args`) live in **`pom.xml`'s
`<properties>` block**, NOT in `application.properties` (the copy there is documentation only —
process-resources doesn't run before `quarkus:build` when invoked directly). Key constraints baked
into those args, change with care:

- `-J-XX:-UseCompressedOops` is required so `Unsafe.arrayIndexScale` matches the runtime (8-byte
  refs); omitting it segfaults jctools.
- JNA, hid4java, jnativehook, dbus, AWT-dependent and Voicemeeter classes are
  `--initialize-at-run-time`; certain AWT font/hint classes are `--initialize-at-build-time`.
- Windows-only GUI-subsystem linker flags (`/SUBSYSTEM:WINDOWS`, `/ENTRY:mainCRTStartup`) are MSVC
  flags injected only via the `os-windows` profile (`native.platform.linker.args`); they break
  GNU ld / ld64, so never add them to the shared block.
- Reachability metadata lives under `src/main/resources/META-INF/native-image/`. Regenerate it with
  the tracing agent via `generate-native-configs.cmd` (Windows) or the commands in README.md.

## Native C++ (`src/main/cpp/`, Windows only)

Visual Studio solution for `SndCtrl.dll` (audio control via Windows Core Audio) and a `SndCtrlTest`
harness (JNI access violations otherwise silently close the app). The built `SndCtrl.dll` is
committed at `src/main/resources/SndCtrl.dll`. The one hardcoded setting is the JNI include dir
under project properties → C/C++ → General → Additional Include Directories.

## Conventions

- Lombok is used throughout (`@Data`, `@Log4j2`, `@RequiredArgsConstructor`, etc.). `@Log4j2` is the
  logging annotation (backed by jboss-logmanager).
- Nullability annotated with `javax.annotation.@Nullable/@Nonnull` (JSR-305) — these feed the TS
  generator's optional-property detection.
- `.editorconfig` defines formatting and a large set of IntelliJ inspection settings; follow it.
- Linux device access needs udev rules and other setup — see `linux.md`.

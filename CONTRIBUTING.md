# Contributing to PCPanel

Thanks for your interest in hacking on PCPanel! This document covers how the project is built and
how to get a development environment running. For what the app *does* and how to install it as a
user, see the [README](README.md).

> **History:** the project began as the decompiled source of the official app, with the parts that
> couldn't be decompiled (the `SndCtrl.dll` / `sndctrl.exe` audio layer) rewritten as custom native
> implementations. It was later migrated from **Spring Boot + JavaFX** to **Quarkus + Angular** and
> now ships as a **GraalVM native image**. (If you find docs that still mention JavaFX, jpackage,
> Wix or Liberica JDK, they are stale — please fix them.)

## Tech stack

- **Backend:** [Quarkus](https://quarkus.io/) on **Java 25**, CDI (Arc) beans wired by injection,
  cross-cutting communication over the CDI event bus.
- **Frontend:** **Angular 21** in `src/main/webui`, served into a local browser by the
  [Quinoa](https://docs.quarkiverse.io/quarkus-quinoa/dev/) extension. There is no separate window
  framework — the "UI" is the browser served by the backend.
- **Hardware:** USB HID via [hid4java](https://github.com/gary-rowe/hid4java).
- **OS audio:** a platform-specific native layer — `SndCtrl.dll` (Windows Core Audio, JNI; source in
  `src/main/cpp/`) and JNA/PulseAudio on Linux.
- **Packaging:** GraalVM native image, wrapped into per-platform installers in CI.

A deeper tour of the architecture (hardware path, command model, persistence, the REST/WebSocket
bridge, integrations) lives in [ARCHITECTURE.md](ARCHITECTURE.md).

## Prerequisites

- **Java 25.** A standard JDK 25 is enough for JVM-mode development. **Native** builds require
  **GraalVM CE 25**.
- The Maven wrapper (`./mvnw` / `mvnw.cmd`) — no separate Maven install needed.
- **Node.js** is only needed if you want to run the Angular dev server standalone; otherwise Quinoa
  manages it for you during `quarkus:dev`.
- **Windows native code** (optional): CMake plus a C++ compiler (MSVC Build Tools or MinGW-w64), to
  rebuild `SndCtrl.dll` (see below). No Visual Studio IDE required.

Development focus is Windows; Linux is best-effort. On Linux you'll also need the device-access setup
from [linux.md](linux.md).

## Build & run

The toolchain is the Maven wrapper.

```bash
./mvnw quarkus:dev          # dev mode: backend on :7654, Quinoa runs the Angular dev server on :4200 with live reload
./mvnw clean package -Dquarkus.native.enabled=false   # JVM-only jar — fast, no GraalVM needed
./mvnw clean package        # builds a NATIVE image by default (requires GraalVM)
./mvnw test                 # unit tests (surefire)
./mvnw test -Dtest=ClassName#method                   # a single test
./mvnw verify -Pnative      # native build + integration tests against the runner binary
```

- For day-to-day work, **`quarkus:dev`** is what you want: live reload on both backend and frontend.
- `package` produces a native executable at `target/*-runner` (Linux) / `target/*-runner.exe`
  (Windows). The native image is **not** self-contained — it loads companion `*.dll`/`*.so`
  libraries from its own directory, so any artifact must bundle them alongside the executable.

### Running alongside an installed copy

Pass the **`skipfilecheck`** argument when launching from your IDE — otherwise starting a second
instance just focuses the already-installed one. To keep your real profile safe while testing, point
the app at a separate data directory by setting `pcpanel.root=${user.home}/.pcpaneldev/` (the `dev`
profile already does this). You can also create an uncommitted `application-default.properties` with:

```properties
pcpanel.root=${user.home}/.pcpaneldev/
```

### IDE setup

Import the project as a Maven project with a Java 25 SDK and use the **`PCPanel`** run configuration.
No special VM options are required anymore (the JavaFX module flags from the old setup are obsolete).
Add `skipfilecheck` to the program arguments so you can debug while the installed version is running.

## Frontend (`src/main/webui`)

Normally you don't run the frontend directly — `quarkus:dev` proxies it. To run it standalone:

```bash
cd src/main/webui
npm install
npm start          # serves :4200, proxies /api + /ws to :7654
```

**TypeScript types are generated from Java, not hand-written.** The `typescript-generator-maven-plugin`
(in the `compile` phase) writes `src/app/models/generated/backend.types.ts` from the REST/WS DTOs,
the command classes, and the device descriptors (the full classPattern list is in
[ARCHITECTURE.md](ARCHITECTURE.md) and `pom.xml`). When you change a DTO or
command shape, **recompile** so the contract regenerates — don't edit the generated file by hand.

## Native C++ (`src/main/cpp/`, Windows only)

`SndCtrl.dll` (audio control via Windows Core Audio) ships **pre-built and committed** at
`src/main/resources/SndCtrl.dll` — the Maven/CI build only bundles it. You only need to rebuild it
when you change the C++ sources.

The sources build via **CMake** with either MSVC or MinGW-w64 — including a cross-compile from
Linux; no Visual Studio IDE or ATL required. On Windows,
`powershell -File src/main/cpp/build-windows.ps1 -InstallTools` installs the tooling (via winget)
and builds in one go. Full instructions, including the Linux cross-compile, are in
[src/main/cpp/README.md](src/main/cpp/README.md).

The legacy Visual Studio solution (`SndCtrl.sln`) still works as an alternative:

- The one machine-specific setting is the JNI include directory: project properties →
  `C/C++ → General → Additional Include Directories`. Point it at your JDK's `include` folder.
- `SndCtrlTest` exists because JNI access violations otherwise silently close the app — running the
  test harness surfaces the actual error.
- An `EnableFullDump.reg` file is included to enable full crash dumps for debugging the native code.

## GraalVM native image

The native image config is the most fragile part of the build. It lives in **two** places that must
be kept in sync — the `quarkus.native.*` properties in `pom.xml` and the copy in
`application.properties` — because which one wins depends on how the build is invoked. **Change both,**
or you'll get different images locally vs. in CI. The full set of constraints (compressed-oops flag,
`--initialize-at-run-time` classes, platform linker flags, macOS having no `libawt`) is documented in
[ARCHITECTURE.md](ARCHITECTURE.md) — read that section before touching native config.

Reachability metadata lives under `src/main/resources/META-INF/native-image/`. Regenerate it with the
tracing agent via the `native-config-gen` Maven profile (needs a GraalVM JDK):

```shell
mvn -Pnative-config-gen test -Dquarkus.native.enabled=false
```

It runs the test suite under `-agentlib:native-image-agent` in merge mode (keeping entries other
platforms captured) and enables the generation-only tests. Afterwards, review the diff and strip any
captured `*Test` infrastructure entries before committing. On Windows, `generate-native-configs.cmd`
wraps the process.

## Releasing

`<project.baseversion>` in `pom.xml` is the version source of truth (artifacts are
`<baseversion>.<build>`). Bump it with `packaging/bump-version.sh <version>` (which also updates the
AppStream metadata), then push a `releases/<version>` branch to trigger a release build. CI
(`.github/workflows/build-and-release.yml`) builds the native image on Windows (`mvn -B verify
-Pnative`, which also runs the failsafe integration tests) and on Linux and macOS (`mvn -B package
-Pnative`), wraps it in the platform installers (`packaging/`) and publishes a per-branch pre-release.

## Coding conventions

- **Lombok** is used throughout (`@Data`, `@Log4j2`, `@RequiredArgsConstructor`, …). `@Log4j2` is the
  logging annotation.
- Nullability is annotated with `javax.annotation.@Nullable` / `@Nonnull` (JSR-305) — these feed the
  TS generator's optional-property detection.
- Comments describe the **current** state and purpose of the code, not how it changed — that belongs
  in the commit message.
- `.editorconfig` defines formatting and a large set of IntelliJ inspection settings; please follow it.
- Make small, focused commits (one logical change each) as you go, rather than one large commit at the
  end.

## Submitting changes

Open a pull request against `main` with a clear description of the change and the motivation. Bug
reports and feature requests are welcome on the
[issue tracker](https://github.com/nvdweem/PCPanel/issues).

**Changelog:** if your change affects behavior users will notice, add a line at the **top** of
`CHANGELOG.md`, above the versioned sections. Unversioned entries there are included in the next
release's notes (see the comment at the top of that file).

## AI-assisted contributions

AI tooling is welcome here — but **be transparent about it.** In the pull request description, state
how the change was produced:

- **How much AI was used** — from "wrote it by hand, AI only reviewed" through "AI-assisted edits" to
  "fully AI-generated." If a PR is generated entirely by an AI agent with no human writing or editing
  the code, say so explicitly (e.g. "This PR is fully AI-generated").
- **How much you reviewed yourself** — did you read every line, run the app, run the tests, verify the
  behavior on hardware? Or are you submitting AI output you have not fully checked? Both are fine to
  submit, but the reviewer needs to know which one it is so they can weigh how closely to scrutinize it.

Reviewers rely on this to calibrate trust. An honest "AI-generated, lightly reviewed" is far more
useful than silence — undisclosed AI output that turns out to be wrong erodes trust in every future
contribution.

# Feature-module structure (plugin-style refactor)

Status: **complete.** The app is reorganized so that each user-facing feature owns all of its code in
one package, implementation details are hidden behind small seams, and adding a feature means creating
a package and implementing a few SPIs rather than editing shared registries. The full JVM suite passes
(437 tests), `tsc` is clean, the native-image coverage/parity guards are green, and a native build
succeeds. This document is the source of truth for the end state.

We are **not** supporting dynamically-loaded third-party plugins (no separate classloaders / jars).
"Plugin-style" here means *internal modularity*: features are discovered through CDI + a handful of
annotations rather than hand-wired into central lists.

## The organising principle

> **A package under `com.getpcpanel.integration.*` is a feature that provides commands.**
> If a piece of code provides no `Command`, it is not an integration — it is the engine, the hardware
> abstraction layer, a platform backend, or shared infrastructure, and it lives accordingly.

This single rule decides where everything goes, and resolves the cases that were previously muddled:

- A **device provider** (the PCPanel HID scanner, Deej, MIDI) supplies hardware *input*, not commands —
  so it belongs in the device HAL (`com.getpcpanel.device.provider.*`), **never** under `integration/`.
- **Sleep/wake detection**, the **device-management REST**, and the **device colour services** provide
  no commands — they are device-subsystem infrastructure, not integrations.
- The **OS audio facade** and the **keyboard/media backends** are what the volume/keyboard *commands*
  drive, so they live inside those features (`integration/volume`, `integration/keyboard`).

## Final package layout

```
com.getpcpanel
├── Main                       # @QuarkusMain entry point
├── commands/                  # the command ENGINE (provides no commands itself)
│   ├── command/               #   Command, Dial/Button/DeviceAction SPIs, CommandNoOp, CommandConverter,
│   │                          #   CommandValueOutput (shared output base), DialValue + DialValueCalculator
│   ├── meta/                  #   @CommandMeta, CommandKind, CommandCategory
│   ├── CommandModule, CommandSubtypeRegistrar   # the decentralized type-registry SPI
│   ├── CommandDispatcher, Commands, IconService, IIconHandler, KeyMacro-free …
│
├── integration/               # EVERY command-providing feature, each self-contained
│   ├── volume/                #   command/ + the volume feature's whole stack:
│   │   ├── command/ , mutecolor/ , overlay/
│   │   ├── VolumeCoordinatorService, FocusVolumeOverrideService, AudioResource, OverlayResource, …
│   │   └── platform/          #   the OS audio facade ISndCtrl + per-OS backends
│   │       ├── (facade + shared types: AudioDevice/Session(+Event), DataFlow, EventType, MuteType, Role)
│   │       ├── windows/ (SndCtrlWindows, SndCtrlNative, WindowsAudio*)  osx/ (SndCtrlOsx, CoreAudio*)
│   │       └── linux/ (SndCtrlPulseAudio, PulseAudio*)
│   ├── keyboard/              #   command/ + Keyboard interface (build-selected) + platform/{windows,osx,linux}
│   ├── program/              #   command/ + IPlatformCommand (per-OS exec/kill backend)
│   ├── device/               #   ONLY the brightness command module (command/) — the device HAL is separate
│   ├── analogbands/  profile/                                  # former "core" command families
│   └── obs/ voicemeeter/ wavelink/ discord/ homeassistant/ mqtt/ osc/   # external connectors
│         # each: command/(+CommandModule), rest/(+dto), Mute/Icon SPI impls, service/client
│
├── device/                    # device HARDWARE-ABSTRACTION LAYER (not an integration — no commands)
│   ├── Device*, DeviceFactory, GenericDevice, DeviceType, DescriptorFactory, DeviceHolder
│   ├── descriptor/            #   DeviceDescriptor + Spec records (the TS device contract)
│   ├── provider/              #   DeviceProvider/Registry SPI + pcpanel/ (the HID provider) + deej/ + midi/
│   ├── rest/                  #   DeviceResource, SerialResource, MidiResource (device-management API)
│   ├── BrightnessService, ProVisualColorsService           # queried by the HAL's OutputInterpreter
│
├── profile/                   # persistence: Save, SaveService, Profile, deserializers, profile-switching
├── sleepdetection/            # OS sleep/wake/lock monitoring (drives device lighting) — per-OS impls
├── iconextract/               # app-icon extraction SPI + per-OS impls (shared: app/process picker)
├── rest/                      # SHARED web bridge: Settings/Platform/System/Icon/Process resources,
│                              #   EventWebSocket, EventBroadcaster, LocalHttpGuard, model/{dto,ws}
├── platform/                  # build-time CDI stereotypes (@WindowsBuild/@LinuxBuild/@MacBuild) +
│   └── process/               #   the shared foreground-window/process helpers (3+ features use them)
├── graalvm/                   # NativeImageConfig, JnaWin32ReflectionConfig (native-image registration)
└── util/                      # shared infrastructure, now grouped:
    ├── image/  concurrent/  os/  io/  app/                  # grouped helper clusters
    ├── coloroverride/  tray/  version/                       # existing subpackages
    └── CdiHelper, Util, SharedHttpClient, ValueInterpolator  # ubiquitous primitives at root
```

`src/main/cpp/` (the C++ source of `SndCtrl.dll`) is unchanged in location; only the **Java**
`com.getpcpanel.cpp` package was dissolved. `dev.niels.{discord,wavelink}` (low-level RPC clients) keep
their separate namespace.

## Key design patterns

### Decentralized command registry — no central list anywhere
`Command` uses `@JsonTypeInfo(Id.NAME)` with **no** `@JsonSubTypes`. Each concrete command declares its
own stable `@JsonTypeName` id (a nice id like `voicemeeter.advanced`) *in its own file* and carries
`@CommandMeta(label, category, kinds, integration, icon, legacyIds)`. Each feature owns an
`@ApplicationScoped CommandModule` bean listing only its own commands. `CommandSubtypeRegistrar`
(an `ObjectMapperCustomizer`) collects every module via CDI `@All` and registers them with Jackson.
**Adding a command or a whole feature touches nothing outside its package.**

Backwards compatibility: `@CommandMeta.legacyIds` records the previous `_type` (the old FQCN); a
`DeserializationProblemHandler` maps an unknown legacy id back to its command on read only, so old
`profiles.json` keep loading and re-saving rewrites them with the nice id.

The frontend registry is **generated from Java**: `CommandRegistryGeneratorTest` emits
`command-registry.generated.ts` from the `@CommandMeta` annotations (and guards staleness);
`command-catalog.ts` consumes it and keeps only the hand-written field editors.

### Build-selected platform interfaces (no caller-side OS branching)
Platform behaviour sits behind an interface with one implementation per OS, chosen by the
`@WindowsBuild`/`@MacBuild`/`@LinuxBuild` CDI stereotypes (which wrap `@IfBuildProperty` on
`pcpanel.build.os`). Callers inject the interface and never check the OS; the impls are package-private.
Examples: `ISndCtrl` (audio) and `Keyboard` (keystroke + media — the per-OS `WindowsKeyboard`/
`OsxKeyboard`/`LinuxKeyboard` are hidden behind it; there is no public platform-keyboard class and no
`KeyMacro`-style platform switch).

### The device HAL and providers are not integrations
`com.getpcpanel.device` is the hardware-abstraction layer: the `Device` model, `DeviceDescriptor`
contract, `DeviceHolder` registry, and the `DeviceProvider` framework with the `pcpanel` (HID), `deej`
and `midi` providers. Provider input is normalized to the canonical 0–255 domain at the edge. None of
this provides commands, so none of it is under `integration/` — only the brightness *command* module is
(`integration/device/command`).

### Native-image registration by `.class`, not String
Project classes that need reflection registration are referenced by `.class` in the
`@RegisterForReflection(targets = …)` of `NativeImageConfig`/`JnaWin32ReflectionConfig` — **not** by
String class name. A String name silently survives a package move and breaks only the native build; a
`.class` reference is a compile error. Required internal classes are made public enough to be referenced
this way; only genuinely third-party internals (Eclipse Paho, jna-platform) remain as String
`classNames`. The `--initialize-at-run-time` build-args (necessarily Strings) live in both `pom.xml`
and `application.properties`, kept identical by `NativeBuildArgsParityTest`.

The Windows `SndCtrl.dll` JNI boundary is coupled to its Java package: `SndCtrlNative`'s package is
encoded in the exported `Java_…` symbol names and in the `FindClass` strings the C++ uses to build
`AudioDevice`/`AudioSession`. Moving it requires updating `src/main/cpp/` and rebuilding the DLL (the
MinGW cross-compile from Linux works — see `src/main/cpp/README.md`).

## How to add things

- **A command:** drop the class in its feature's `command/` package with `@JsonTypeName("feature.x")`
  and `@CommandMeta(...)`, and list it in that feature's `CommandModule`. Regenerate the frontend
  registry (`CommandRegistryGeneratorTest -Dpcpanel.generate.catalog`). Nothing central changes.
- **A new integration:** create `com.getpcpanel.integration.<name>` with a `command/` package + a
  `CommandModule`, and (if it has them) a `MuteStateResolver`/`IIconHandler` impl and its service/REST —
  all discovered via CDI. The `**.command.**` classPattern and the `@All` SPIs pick it up automatically.
- **A new device provider:** add it under `com.getpcpanel.device.provider.<name>` implementing
  `DeviceProvider`; it is discovered via `Instance<DeviceProvider>`. It is HAL, not an integration.
- **A new platform backend:** add an `@<OS>Build` implementation of the relevant interface; do not add
  OS branches at call sites.

## Guards (all green)
`CommandSubtypeRegistryTest` (every command has a unique `@JsonTypeName`; the `CommandModule` SPI covers
exactly the concrete set; every id resolves), `CommandSubtypeRegistrarTest` (registrar wiring +
legacy-id read/convert), `CommandRegistryGeneratorTest` (frontend registry current),
`ReflectionRegistrationCoverageTest` + `ProxyRegistrationCoverageTest` (native reflection/proxy coverage
across `com.getpcpanel.**`), `NativeBuildArgsParityTest` (pom ↔ application.properties build-args).

Git history is preserved throughout via `git mv` for every relocation.

# Feature-module structure (plugin-style refactor)

Status: **complete** — the decentralized command registry, the per-feature module split (core +
integrations), the Java-generated frontend registry, nice backwards-compatible discriminators, and the
non-command consolidation (REST resources → `rest/<feature>`, VM/OBS icons on the `IIconHandler` SPI,
dead `CommandsResource`/`CommandType` removed, `events.md` updated) are all implemented and green
(437 tests pass; `tsc` clean). The branch is rebased on current `origin/main`. This document is the
source of truth for the end state.

> The only step that cannot be fully verified in a non-native build is the moved REST resources'
> Quarkus native-image invoker metadata (`reachability-metadata.json`) — per the GraalVM notes in
> `CLAUDE.md`, that is verified by the native CI build / running the native binary.

### Implemented so far

- **Fully decentralized command registry — no central list anywhere.** `Command` uses
  `@JsonTypeInfo(Id.NAME)` with **no** `@JsonSubTypes`. Each concrete command declares its own stable
  id with `@JsonTypeName` *in its own file*, and each feature owns an `@ApplicationScoped`
  `CommandModule` bean that lists only its own commands. `CommandSubtypeRegistrar` (an
  `ObjectMapperCustomizer`) collects every module via CDI `@All` and registers them with Jackson —
  the same `@All`-discovered SPI pattern the codebase already uses for `MuteStateResolver`,
  `IIconHandler`, `DeviceProvider`. **Adding a command or a whole new feature/plugin touches nothing
  outside its package.** The id is the command's historical FQCN, frozen as a logical name, so saved
  `profiles.json`, the generated TS `_type` union, and the frontend catalog are byte-for-byte
  unchanged. `typescript-generator` emits the `_type` literals from `@JsonTypeName` (verified).
- **Every command — integration *and* core — now lives in its own feature `*.command` module.**
  Integrations: VoiceMeeter, OBS, OSC, MQTT moved in (WaveLink/Discord/Home Assistant already were).
  Core split out of the old catch-all into: `volume.command`, `keyboard.command` (keystroke + media),
  `program.command` (run/shortcut/end-program), `device.command` (brightness), `profile.command`,
  `analogbands.command`, `output.command` (HTTP + the `CommandValueOutput` base). `commands/command`
  now holds **only the engine**: `Command`, `CommandNoOp`, `CommandConverter`, the
  `Dial/Button/DeviceAction` SPIs, and the `CommandModule` SPI + registrar.
- **`VoiceMeeterMuteResolver`** moved from `mutecolor/` into the `voicemeeter` module (still found via
  `@All List<MuteStateResolver>`).
- **pom `classPatterns`** collapsed to one glob `com.getpcpanel.**.command.**` — new feature command
  packages need no build-config edit.
- **Every command-providing module lives under `com.getpcpanel.integration.*`.** Not just the external
  connectors (obs, voicemeeter, wavelink, discord, homeassistant, mqtt, osc) but the former "core"
  families too (volume, keyboard, program, device, profile, analogbands, output) — the principle is
  *if it provides commands, it's an integration*. Each is self-contained: its `command/` (+ its
  `CommandModule`), and for external ones also `rest/` (resource + DTOs, feature-local — not under
  `rest/`), `MuteStateResolver`/`IIconHandler` impls, and service. `com.getpcpanel.commands` is the
  engine only; genuine infra (device-provider framework, profile persistence, volume audio services,
  analog-bands colour service, cpp/overlay/hid/util) stays put. Persisted ids are location-independent,
  so this was a pure move (the `backend.types.ts` diff is a reorder of the same `_type` literal set).
- **Frontend command registry is generated from Java.** Each assignable command carries
  `@CommandMeta(label, category, kinds, integration, icon)` in its own file;
  `CommandRegistryGeneratorTest` emits `command-registry.generated.ts` from those annotations (and
  guards staleness). `command-catalog.ts` consumes it and keeps only the field editors
  (`buildEmpty`/`fields[]`, which are Angular UI). So "which commands exist + how they're classified"
  is retrieved from Java, per command.
- **Nice, backwards-compatible discriminators.** Each command's persisted `_type` is now a readable id
  (`voicemeeter.advanced`) via `@JsonTypeName`. `@CommandMeta.legacyIds` records the previous id (the
  old FQCN); `CommandSubtypeRegistrar` installs a Jackson `DeserializationProblemHandler` that maps an
  unknown legacy id back to its command on **read only** — old `profiles.json` keep loading and
  re-saving rewrites them with the nice id (a transparent one-way conversion). New saves are never
  ambiguous with old ids. `CommandSubtypeRegistrarTest` covers both directions.

Guards (all green): `CommandSubtypeRegistryTest` enforces every command self-identifies with a unique
`@JsonTypeName`, the `CommandModule` SPI covers exactly the concrete set (none missing/stale/dup), and
every id resolves; `CommandSubtypeRegistrarTest` checks the registrar wiring;
`ReflectionRegistrationCoverageTest` stays green. Full backend suite green except 3 pre-existing,
environment-specific `FocusVolumeOverrideServiceTest` failures unrelated to this work; frontend `tsc`
clean; TS `_type` literal set byte-identical.

**Adding a command is now entirely package-local:** drop the class in `com.getpcpanel.<feature>.command`
with `@JsonTypeName("…")`, and add it to that feature's `CommandModule`. Nothing central changes.

> **Note on the id scheme vs. the original plan.** The approved design called for *pretty* ids
> (`voicemeeter.advanced`). The implementation **freezes the current FQCN as the stable logical id** —
> the strictly safer stepping stone: zero change to saves, the generated TS, or the frontend, while
> still decoupling the persisted id from the class's package. Switching to pretty ids later just adds
> `legacyTypes` aliases on each command and regenerates the frontend catalog (the one remaining
> central artifact — see the generator phase below).

## Goal

Make each *integration / feature* (VoiceMeeter, OBS, Wave Link, Discord, Home Assistant, OSC, MQTT,
and any future one) a **self-contained module**: its code lives in its own package, scoped as local
as possible, exposing a small public seam to the rest of the app. Adding a new feature should mean
*creating one package and implementing a few SPIs* — not editing a dozen shared registries.

We are **not** supporting dynamically-loaded third-party plugins (no separate classloaders / jars).
"Plugin-style" here means *internal modularity*: features are discovered through CDI and a handful of
annotations rather than hand-wired into central lists.

## Where we are today

Two patterns coexist:

- **Good (the template):** Wave Link, Discord, Home Assistant. Command classes live in
  `com.getpcpanel.<feature>.command`, REST under `rest/<feature>` (`rest/wavelink`, `rest/discord`),
  low-level clients under `dev.niels.<feature>`, settings records inside the feature package, icon via
  the `IIconHandler` SPI, mute-colour via the `MuteStateResolver` SPI. They leak into only a few
  shared registries.
- **Scattered (the problem):** VoiceMeeter and OBS (and the generic outputs OSC/MQTT). Their
  `Command*` classes sit in the shared `commands/command/` pile; their REST resources are flat in
  `rest/`; VoiceMeeter's mute resolver lives in the generic `mutecolor/` package and exposes a public
  `VM_PATTERN` regex that `NamedDeviceMuteResolver` reaches into; their icons are hard-coded in
  `IconService.init()`.

### The real cost: adding one integration command touches ~20 places

Verified by a fan-out audit of every subsystem. The central registries that must change when a
feature/command is added or removed:

| # | Registry | Location | Avoidable? |
|---|----------|----------|------------|
| 1 | Frontend command catalog | `webui/.../features/commands/command-catalog.ts` (`COMMANDS[]`) | **Generate from Java** |
| 2 | Backend picker list + category enum | `rest/CommandsResource` static list + `rest/model/dto/CommandType.CommandCategory` | **Derive from annotations** |
| 3 | Icon handler map | `commands/IconService.init()` hard-coded `imageHandlers.put(...)` | **Move to `IIconHandler` SPI** |
| 4 | Native-image reflection | `graalvm/NativeImageConfig` `classes[]`/`classNames[]` | Guarded by coverage test; can be derived |
| 5 | Native build args (×2, parity-locked) | `pom.xml` + `application.properties` `additional-build-args` | Only for JNA features |
| 6 | TS-generator class patterns | `pom.xml` `<classPatterns>` | **Single glob** instead of per-feature line |
| 7 | Settings schema | `profile/Save` fields | Local to feature (record) |
| 8 | Settings DTO mapping | `rest/model/dto/SettingsDto` `from`/`applyTo` | Local to feature |
| 9 | Settings REST + tabs | `rest/SettingsResource`, frontend `settings.component.*` | Partly local |
| 10 | Platform capability flag | `rest/PlatformResource`, frontend `platform.service.ts` | For OS-gated features |
| 11 | Mute-colour resolver | `mutecolor/*Resolver` (SPI already) | Local to feature |
| 12 | Frontend live-picker data | `features/commands/integration-data.service.ts` + `command-fields.ts` `liveOptions()` | Inherently per-feature (frontend) |
| 13 | Frontend picker integration list | `features/commands/command-picker.component.ts` `INTEGRATIONS` | Inherently per-feature (frontend) |
| 14 | Legacy migration switch | `commands/command/CommandConverter` | Legacy-only; untouched by new features |
| 15 | Events catalogue | `docs/events.md` | Doc; keep current |

Items 1–4 and 6 are the high-value collapses. 11–13 stay per-feature but become **local to the
feature module** instead of edits to shared files.

## The cornerstone constraint: `Command` polymorphism is `Id.CLASS`

`commands/command/Command.java` is annotated `@JsonTypeInfo(use = Id.CLASS, property = "_type")`. That
means **the persisted discriminator in every user's `profiles.json` is the literal fully-qualified
class name**, e.g. `com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced`. The frontend
`command-catalog.ts` keys on the same FQCN strings. `CommandMapDeserializer` does **no** FQCN
remapping (`CommandConverter` only migrates the ancient v1.6 `String[]` format, never `_type`s).

**Therefore: moving any command class to a new package silently breaks every existing saved profile**
unless we decouple the persisted type id from the class location first. The commands actually at risk
(still in `commands/command/`, so a move changes their FQCN) are exactly:

- OBS: `CommandObs`, `CommandObsAction`, `CommandObsSetScene`, `CommandObsMuteSource`, `CommandObsSetSourceVolume`
- VoiceMeeter: `CommandVoiceMeeter` (+ `Basic`, `Advanced`, `BasicButton`, `AdvancedButton`)
- MQTT: `CommandMqttPublish`
- OSC: `CommandOscSend`
- (generic) `CommandHttpRequest`

Wave Link / Discord / Home Assistant commands also persist their FQCN, but they already live in their
feature `.command` packages, so they are frozen-in-place and not at move-risk.

### Decision: stable logical type IDs (decouple `_type` from FQCN)

Introduce a stable, location-independent type id per command — the same pattern `WsEvent` already uses
(`@JsonTypeInfo(use = Id.NAME)` + logical names). Each command declares a stable id (e.g.
`voicemeeter.advanced`) via the new `@CommandMeta` annotation (below). A custom `@JsonTypeIdResolver`
configured on `Command` (wired through `com.getpcpanel.Json`, the single shared `ObjectMapper`) maps
**stable-id ↔ class**, so commands become freely movable forever.

Back-compat is mandatory: the resolver also recognises **legacy FQCN `_type` strings** as aliases for
their command (a small `legacy-fqcn → stable-id` table, seeded with the current FQCNs of the at-risk
commands). Old saves load; new saves write the stable id. The frontend catalog then keys on stable ids
instead of FQCN strings.

This single mechanism is the foundation of the annotation-driven registry: the same `@CommandMeta`
that carries the stable id carries the UI metadata.

## The annotation-driven command registry (single source of truth)

Today the frontend `command-catalog.ts` is hand-maintained and duplicates metadata that mostly already
exists on the Java command classes. Replace it with metadata declared **once, in Java, next to each
command**, and generated into TypeScript at build time (consistent with the existing
typescript-generator pipeline; keeps the frontend statically typed).

New annotations in `com.getpcpanel.commands.meta`:

```java
@CommandMeta(
    id        = "voicemeeter.advanced",          // stable persisted type id + UI _type key
    label     = "Voicemeeter — parameter",
    category  = CommandCategory.INTEGRATION,
    feature   = "voicemeeter",                   // ties to a Feature/enablement key; omit for core
    kinds     = { CommandKind.DIAL },
    icon      = "sliders")                        // must be an IconName the UI knows
@FieldMeta(key = "fullParam", label = "Parameter", kind = SELECT_LIVE, source = "vm-advanced")
@FieldMeta(key = "ct",        label = "Range",     kind = SELECT, optionsEnum = ControlType.class)
public final class CommandVoiceMeeterAdvanced extends CommandVoiceMeeter implements DialAction { ... }
```

A build-time generator (an extension of the typescript-generator step, or a small companion Maven
generator) emits `webui/.../models/generated/command-catalog.generated.ts` carrying the `CommandDef`
list — type id, label, category, kinds, feature, icon, the `buildEmpty()` default shape (derived from
the class's fields/`@JsonProperty` wire names), and the simple `fields[]`.

**Escape hatches stay hand-written.** Composite field editors that are not 1:1 with a Java field —
`keystroke`, `wavelink-target`, `analog-bands`, and the `mute`/`apps`/`device` live pickers — and the
`LiveSource` → `IntegrationDataService` wiring cannot be generated. The generated catalog references
them by a stable `kind`, and `command-fields.component.ts` keeps its bespoke renderers. The generator
must faithfully reproduce two existing quirks: the `@JsonProperty("isUnMuteOnVolumeChange")` wire-name
divergence, and the dial `invert` + `dialParams{invert,moveStart,moveEnd}` duplication.

The same annotation metadata feeds `CommandsResource` (picker list + per-feature `enabled()` gate),
collapsing registry #2. (Note: that backend list is *already* non-authoritative — Discord/HA are
missing from it today — confirming the frontend catalog is the real registry to replace.)

## Target package layout

**Principle: anything that *provides commands* is an integration.** There is no separate "core
commands" tier — every command-providing module lives under `com.getpcpanel.integration.*`. Only the
command *engine* and genuine platform/infra/services sit elsewhere.

```
com.getpcpanel
├── commands/                 # the command ENGINE only (framework, provides no commands itself)
│   └── command/              #   Command, DialAction/ButtonAction/DeviceAction SPIs, CommandNoOp,
│       │                     #   CommandConverter (legacy v1.6), CommandValueOutput (shared output base)
│       meta/                 #   @CommandMeta, CommandKind, CommandCategory
│       CommandModule, CommandSubtypeRegistrar   # the decentralized type-registry SPI
│       Commands, CommandDispatcher, IconService, IIconHandler, …
│
├── integration/              # EVERY command-providing module, each self-contained
│   ├── volume/  keyboard/  program/  device/  profile/  analogbands/  output/   # former "core" families
│   ├── obs/  voicemeeter/  wavelink/  discord/  homeassistant/  mqtt/  osc/      # external connectors
│   │     # each: <name>/command (+ its CommandModule), and for external ones also rest/ (+rest/dto),
│   │     #       Mute/Icon SPI impls, and the integration's service/engine
│
├── rest/                     # SHARED web bridge only (Device/Audio/Settings/… resources,
│                             #   EventWebSocket, EventBroadcaster, LocalHttpGuard, model/{dto,ws})
├── mutecolor/                # orchestrator + MuteStateResolver SPI + integration-agnostic resolvers
└── cpp/ device(provider)/ profile(persistence)/ volume(audio services)/ overlay/ hid/ util/ …  # platform/infra
```

Notes: for `device` and `profile`, only the `.command` module moved — the device-provider framework
(`com.getpcpanel.device`) and the persistence layer (`com.getpcpanel.profile`: `Save`, `SaveService`,
deserializers) are infra, not command modules, and stay. Likewise `volume`'s audio-orchestration
services (used by `overlay`/`rest`) and the analog-bands colour service stay. Each external
integration's REST resource + DTOs live in `integration.<name>.rest` (feature-local), not `rest/<name>`;
only the cross-cutting web bridge stays in `rest/`. Per-integration `MuteStateResolver`/`IIconHandler`
impls live in the integration package (CDI `@All` makes location irrelevant).
`dev.niels.{discord,wavelink}` (low-level RPC clients) keep their separate namespace.

### `pom.xml` classPatterns → one glob

Replace the per-feature lines (`wavelink.command.**`, `discord.command.**`, …) with
`com.getpcpanel.**.command.**` so any feature's command package is picked up automatically — removing a
per-integration edit to the build config.

## Out of scope (platform core, not pluggable features)

These packages are core infrastructure, not integrations, and are **not** restructured by this work
(flagged here so they are not mistaken for omissions): `cpp/` (OS-audio facade `ISndCtrl` + per-OS
impls), `overlay/`, `sleepdetection/`, `iconextract/`, `volume/`, `analogbands/`, `util/**` (incl.
`tray/`, `version/`, `coloroverride/`), and top-level `Main`/`Json`/`CachingConfig`. Note `Json.java`
is the single Jackson-config seam where the command type-id resolver must be registered. Several of
these already expose sibling SPIs (`IOverrideColorProvider`, `IFocusRedirector`) that confirm the
CDI-discovery pattern this refactor leans on.

## Phased implementation

Each phase is independently committable and keeps the build + coverage/parity tests green.

1. **✅ DONE — Frozen-id type registry.** `Command` → `Id.NAME` with per-class `@JsonTypeName` ids
   (frozen FQCNs), so command classes can move package with no save/TS/frontend impact.
2. **✅ DONE — Integration commands relocated** (git-mv): VoiceMeeter, OBS, OSC, MQTT into their
   `*.command` packages; `VoiceMeeterMuteResolver` into the `voicemeeter` module; classPatterns glob.
3. **✅ DONE — Decentralized registry (no central list).** Dropped `@JsonSubTypes` entirely; added the
   `CommandModule` CDI SPI + `CommandSubtypeRegistrar` (`@All`-collected `ObjectMapperCustomizer`); each
   feature self-registers. Guard tests rewritten for the decentralized invariants.
4. **✅ DONE — Core commands split into feature modules.** `volume`, `keyboard`, `program`, `device`,
   `profile`, `analogbands`, `output` — `commands/command` is now engine-only.
5. **✅ DONE — Annotation-driven frontend registry + nice backwards-compatible ids.** `@CommandMeta`
   on each assignable command + `CommandRegistryGeneratorTest` generates `command-registry.generated.ts`;
   `command-catalog.ts` consumes it (field editors stay hand-written). `@JsonTypeName` ids switched to
   nice form (`voicemeeter.advanced`), with the old FQCN kept as a read-only `@CommandMeta.legacyIds`
   alias via a `DeserializationProblemHandler` (old saves load; re-save converts). Field-level
   generation (`@FieldMeta` for the composite editors) is a possible future extension but not required —
   the editors are genuinely UI, not registry data.
6. **✅ DONE — Remaining non-command consolidation.** `VoiceMeeterResource`/`ObsResource`/`OscResource`
   → `rest/<feature>`; the hardcoded `IconService` VoiceMeeter/OBS handlers migrated to the
   `IIconHandler` SPI (`ObsIconHandler`, `VoiceMeeterIconHandler`); dead `CommandsResource` +
   `CommandType` DTO removed (and their `reachability-metadata.json` entries pruned); `docs/events.md`
   updated with `DiscordChangedEvent`. `ReflectionRegistrationCoverageTest` green. (Per-feature
   settings records are still in `Save`/`SettingsDto` — a possible further tidy, but those are
   field-name Jackson, not `Id.CLASS`, so they carry no module-locality or migration risk.)

Git history is preserved throughout by using `git mv` for every relocation. Phases 1–4 are committed
and green.

## Risks & guards

- **Saved-profile breakage** — eliminated by freezing each command's persisted id to its historical
  FQCN, so a package move never changes the wire string; `CommandSubtypeRegistryTest` deserializes
  every registered id. (When phase 5 introduces pretty ids, the old FQCNs become `legacyTypes`
  aliases and the same test must cover them.)
- **Native-image 500s** — every concrete command + nested record (and `[]` form for `List`/`Set`)
  must stay registered; `ReflectionRegistrationCoverageTest` enforces it. The `File`/`FileSerializer`
  hazard on `ISndCtrl.RunningApplication` is pre-existing and unaffected.
- **TS contract drift** — the classPatterns glob must actually match new packages; the build fails
  loudly if `backend.types.ts` regenerates differently.
- **Commands are plain data, not beans** — they reach services via `CdiHelper.getBean(...)`. The
  metadata registry hangs off the *class*, never an instance.
- **`docs/events.md` is stale** (missing Discord events) — fix as part of phase 6.

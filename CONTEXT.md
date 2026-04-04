# PCPanel Migration — Current Status & Remaining Work

This file tracks what has been completed from [PLAN.md](./PLAN.md) and what remains.
Last updated: 2026-04-03

---

## ✅ Completed Steps

### Phase 1: pom.xml — Swap Framework
- **DONE** — Spring Boot parent replaced with Quarkus BOM
- **DONE** — Spring dependencies removed; Quarkus extensions added:
  `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-scheduler`, `quarkus-cache`,
  `quarkus-websockets-next`, `quarkus-quinoa`
- **DONE** — JavaFX dependencies removed
- **DONE** — OBS Jetty deps removed; OBS disabled (see Phase 9)
- **DONE** — Build plugins updated: `quarkus-maven-plugin` replaces Spring/JavaFX plugins
- **DONE** — `application.properties` fully migrated to Quarkus keys
  (`quarkus.http.port=7654`, `quarkus.quinoa.*`, `quarkus.jackson.*`, `quarkus.log.*`)

### Phase 2: Main Entry Point
- **DONE** — `MainFX.java` deleted; `Main.java` replaced with `@QuarkusMain` / `QuarkusApplication`
- **DONE** — `FileChecker` startup logic runs inside `QuarkusApplication.run()`
- **DONE** — Lifecycle events use CDI `StartupEvent` / `ShutdownEvent`

### Phase 3: CDI — Spring → Quarkus Annotation Sweep
- **DONE** — All 92+ beans converted: `@Service`/`@Component` → `@ApplicationScoped`, `@Autowired` → `@Inject`, `@Value` → `@ConfigProperty`
- **DONE** — Platform conditionals (`@ConditionalOnWindows` etc.) replaced with CDI `@Produces` factory methods (in `com.getpcpanel.spring.*` producer classes)
- **DONE** — Spring `ApplicationEventPublisher` replaced with CDI `Event<T>` / `@Observes` throughout
- **DONE** — Caching: `@Cacheable`/`@CacheEvict` → `@CacheResult`/`@CacheInvalidate` (Quarkus cache)
- **DONE** — Scheduling: `@Scheduled` → Quarkus `@Scheduled(every=...)` (WaveLink, VoiceMeeter, CachingConfig)
- **DONE** — HTTP client: `RestTemplate` removed; `VersionChecker` and `WaveLinkClientImpl` use `java.net.http.HttpClient`

### Phase 4: Remove JavaFX — UI Package
- **DONE** — Entire `src/main/java/com/getpcpanel/ui/` package deleted
- **DONE** — `PCPanelProUI.java`, `PCPanelMiniUI.java`, `PCPanelRGBUI.java` deleted;
  remaining device classes: `PCPanelProDevice`, `PCPanelMiniDevice`, `PCPanelRGBDevice`
  (no-op stubs for `closeDialogs()` / `showLightingConfigToUI()` remain — see below)
- **DONE** — FXML and JavaFX CSS assets removed from `src/main/resources/assets/`
- **DONE** — `TrayServiceAwt` and `TrayServiceWayland` kept as-is (no JavaFX dependency)

### Phase 5: REST API Layer
- **DONE** — `DeviceResource` (`/api/devices`) — device listing, rename, profiles, button/dial/knobsettings, lighting
- **DONE** — `AudioResource` (`/api/audio`) — audio devices (all/input/output), sessions, running applications
- **DONE** — `SettingsResource` (`/api/settings`) — settings, MQTT, WaveLink
- **DONE** — `EventWebSocket` + `EventBroadcaster` — WebSocket at `/ws/events` for device connect/disconnect, knob rotate, button press

### Phase 6: Angular Frontend (via Quinoa)
- **DONE** — Angular 19.2.20 project scaffolded in `src/main/webui/`; build output → `dist/pcpanel`
- **DONE** — Quinoa configured: `quarkus.quinoa.build-dir=dist/pcpanel`, `%dev.quarkus.quinoa.dev-server.port=4200`
- **DONE** — Angular pages: `HomeComponent`, `DeviceComponent`, `LightingComponent`, `SettingsComponent`
- **DONE** — Angular services: `DeviceService`, `AudioService`, `SettingsService`, `EventService` (WebSocket)
- **DONE** — Dev proxy config: `npm start` forwards `/api` and `/ws` to Quarkus on port 7654

### Phase 9: OBS WebSocket Integration
- **DONE** — OBS WebSocket 5 client implemented (see Phase 9 in Remaining Work → now complete)

---

## ❌ Remaining Work

### Phase 4 (partial): Device model cleanup ✅
- **DONE** — `closeDialogs()` and `showLightingConfigToUI()` abstract methods removed from `Device.java`;
  no-op overrides removed from `PCPanelProDevice`, `PCPanelMiniDevice`, `PCPanelRGBDevice`.
  `disconnected()` in `Device.java` no longer calls `closeDialogs()`.
  The full Device/DeviceModel split described in 4c has not been done yet — `Device.java`
still mixes some UI-era concerns into the business class.

### Phase 5 (partial): Missing REST resources
The following resources from the plan are **not yet created**:
- `ProfileResource` (`/api/profiles`) — profile commands are embedded in `DeviceResource`; consider extracting to a dedicated resource
- `CommandResource` (`/api/commands`) — button/dial command assignment endpoints exist inside `DeviceResource` but a dedicated `CommandResource` was planned; the full command-type catalog (34 types) is not yet exposed
- `LightingResource` (`/api/lighting`) — lighting endpoints exist inside `DeviceResource`; consider extracting
- ~~`IconResource` (`/api/icons`)~~ **DONE** — `GET /api/icons?path=...&size=...` returns icon PNG for a file path
- ~~`ProcessResource` (`/api/processes`)~~ **DONE** — `GET /api/processes` returns running processes with base64 PNG icons

### Phase 6 (partial): Angular UI completeness
The Angular frontend has scaffolding but is incomplete vs. the plan:
- ~~**Missing**: `CommandConfigComponent`~~ **DONE** — modal for configuring button/dial actions; covers all 25 command types with type-specific form fields
- ~~**Missing**: `AudioPickerComponent`~~ **DONE** — process/device picker with thumbnail list; used inside `CommandConfigComponent`
- ~~**Missing**: `ProcessService`~~ **DONE** — Angular service wrapping `GET /api/processes`
- ~~**Missing**: `DialParamsEditorComponent`~~ **DONE** — reusable invert/moveStart/moveEnd sub-form
- **`DeviceComponent`** updated — `editDial()` / `editButton()` now open `CommandConfigComponent` modal; save paths call `setDialCommands` / `setButtonCommands`
- ~~**Missing**: Full `DeviceDetailComponent` with a visual representation of each device type~~ **DONE** — Home component now shows:
  - PCPanel RGB / Mini: row of CSS knobs (rotary) with live rotation from WebSocket
  - PCPanel Pro: row of 5 knobs PLUS row of 4 sliders (correct hardware layout), with live thumb position from WebSocket
  - Button row below hardware visual
  - Global brightness slider in bottom bar (reads/writes lighting config)
  - Knob/slider positions update in real-time from `knob_rotate` WebSocket events
- ~~**Missing**: OSC settings tab~~ **DONE** — Settings page has General / OBS / VoiceMeeter / Overlay / MQTT / OSC tabs
- **Missing**: Overlay settings are in the settings page but the overlay itself (the on-screen volume bar) has no web equivalent yet
- **Partial**: WaveLink tab not in settings (WaveLink fields not in SettingsDto backend either)

### Phase 7: Native Image Support (GraalVM)
- **DONE** — `NativeImageConfig.java` (`com.getpcpanel.graalvm`) — single `@RegisterForReflection(targets={...})` hub registering all 34 command classes, `Commands`, `CommandsType`, `DeviceSet`, `DialCommandParams`
- **DONE** — `src/main/resources/META-INF/native-image/reflect-config.json` — JNA interfaces: `SndCtrlNative`, `VoicemeeterInstance` + inner Structures, `Shell32Extra`, `IShellItemImageFactory`, `SIZEByValue`, `com.sun.jna.Native/Structure/Pointer`, `WinUser$WindowProc/WNDCLASSEX`
- **DONE** — `quarkus.native.resources.includes=SndCtrl.dll,*.so,*.dll,*.dylib` added to `application.properties`
- **DONE** — `%native.quarkus.native.additional-build-args` added: `--initialize-at-run-time=com.sun.jna`, `--initialize-at-run-time=org.hid4java`, `-H:ReflectionConfigurationFiles=...`
- **DONE** — `native` Maven profile already present in `pom.xml` (`<quarkus.native.enabled>true</quarkus.native.enabled>`)
- **NOT DONE** — End-to-end native build test on Windows (requires GraalVM toolchain, deferred to CI)

### Phase 8: GitHub Actions Updates
- **DONE** — `maven-build-installer-windows.yml` fully rewritten:
  - WiX / MSI installer steps removed
  - `actions/setup-java` (Liberica JDK+FX) replaced with `graalvm/setup-graalvm@v1` (**Java 25**, `graalvm-community`)
  - Both `buildWindows` and `buildLinux` jobs run `mvn -B package -Pnative`
  - Windows artifact: `target/*-runner.exe`; Linux artifact: `target/*-runner`
  - `preRelease` job updated to download `windows-native` / `linux-native` artifacts
  - Tag name simplified: `latest-<safe-branch>` (removed `-windows-` prefix)
- **Java version decision (updated)** — pom.xml compile target is **Java 25** (`<java.version>25</java.version>`). The GraalVM runner in CI also uses **JDK 25** (`JAVA_VERSION: '25'`). Local devs should install JDK 25. All action versions in the workflow are v4 (checkout, upload-artifact, download-artifact).

### Phase 9: OBS WebSocket Integration
- **DONE** — `ObsWebSocketClient.java` (`com.getpcpanel.obs`) — custom OBS WebSocket 5 client built on `java.net.http.WebSocket`:
  - Handles hello/identify handshake (op 0/1/2)
  - SHA-256 + Base64 password authentication
  - Request/response correlation via UUID + `CompletableFuture`
  - Subscribes to `InputMuteStateChanged` events (eventSubscriptions=8)
  - Operations: `getSourcesWithAudio`, `getSourcesWithMuteState`, `getScenes`, `setSourceVolume`, `toggleSourceMute`, `setSourceMute`, `setCurrentScene`
- **DONE** — `OBS.java` rewritten from stub to full CDI service:
  - Auto-connects on startup if `save.obsEnabled=true`
  - `@Scheduled(every="30s")` reconnect loop
  - Fires `OBSConnectEvent(true/false)` and `OBSMuteEvent` CDI events
  - `test()` method for connection testing from settings UI
- **DONE** — `application.properties` OBS hard-disable (`pcpanel.obs.enabled=false`) removed; OBS is now enabled/disabled per user settings (`save.obsEnabled`)
- **NOT DONE** — `ObsConnectedVolumeService` fires on OBSConnectEvent and triggers dial commands — already wired up

---

## Recent Bug Fixes (2026-04-04)

- **Jackson deserialization of `LightingConfig`**: `LightingConfig` uses non-standard (non-JavaBeans) accessors (`knobConfigs()` instead of `getKnobConfigs()`), has no setters for its array fields, and a private no-arg constructor. Jackson couldn't set private fields like `knobConfigs`, `sliderConfigs`, `sliderLabelConfigs` — they stayed as empty `{}`. Fixed by adding `@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)` to `LightingConfig`, which enables direct field access for both serialization and deserialization.
- **`SetMuteOverrideService.getAllDeviceLightingCapable` AIOOBE**: Added bounds checking before accessing `oLightConfig.knobConfigs()[idx]` and `oLightConfig.sliderConfigs()[slider]` to prevent `ArrayIndexOutOfBoundsException` if array lengths don't match.
- **Missing initial lighting send**: When a device connects, its saved lighting config was never sent to the physical device. Fixed by adding `device.setLighting(device.lightingConfig(), true)` in `DeviceHolder.deviceAdded()`.

---

## Known Issues / Decisions Needed

1. **Overlay (on-screen volume display)** — The existing `Overlay.java` (JavaFX window) was deleted with the UI package. A replacement for the overlay has not been designed. Options: keep it as a system tray tooltip, implement it as an always-on-top browser window via a separate Quarkus endpoint, or defer entirely.

2. ~~**`IconResource` / process icons**~~ — **DONE** — `GET /api/icons?path=...` serves PNG icons; `GET /api/processes` returns processes with inline base64 icons. The `icon` field on `AudioSession` is a base64 PNG string (set from JNI on Windows).

3. ~~**OBS re-enablement**~~ — **DONE** — Phase 9 complete. Custom `ObsWebSocketClient` built on `java.net.http.WebSocket` implements OBS WebSocket protocol 5. `OBS.java` is a full CDI service with auto-reconnect.

4. **Profile/Command data model in Angular** — The command types (34 subtypes of `Command`) need to be modelled in TypeScript for the `CommandConfigComponent`. Jackson polymorphism is via `@class` discriminator; the Angular command editor must handle all 34 types.

---

## Build Commands (verified)

```bash
# Java backend
mvn compile          # fast compile check
mvn package          # full JVM build

# Angular frontend
cd src/main/webui
npm run build        # production build → dist/pcpanel
npm start            # dev server on :4200, proxies /api and /ws → :7654
```

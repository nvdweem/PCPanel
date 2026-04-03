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

### Phase 9: OBS Disable
- **DONE** — OBS code present but disabled via `application.properties` flag (`pcpanel.obs.enabled=false`);
  Jetty deps removed from pom.xml; OBS classes are dormant

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
- **Missing**: Full `DeviceDetailComponent` with a visual representation of each device type (Pro: knobs+sliders, Mini: 4 buttons, RGB: grid)
- **Missing**: Overlay settings are in the settings page but the overlay itself (the on-screen volume bar) has no web equivalent yet
- **Partial**: `SettingsComponent` covers General/OBS/VoiceMeeter/Overlay/MQTT but lacks OSC and WaveLink tabs

### Phase 7: Native Image Support (GraalVM)
Nothing in this phase has been started:
- [ ] Add `@RegisterForReflection` to all 34 command classes
- [ ] Create `reflect-config.json` for JNA interfaces and hid4java internals
- [ ] Configure `quarkus.native.resources.includes` for hid4java native libs
- [ ] Add `%native` profile to `application.properties` with `--initialize-at-run-time` args
- [ ] Add `native` Maven profile to `pom.xml`
- [ ] Test native build end-to-end on Windows

### Phase 8: GitHub Actions Updates
Not started:
- [ ] Replace Liberica JDK with `graalvm/setup-graalvm@v1` in the Windows build job
- [ ] Add `mvn package -Pnative` build step
- [ ] Update artifact paths from `*.msi`/`*.deb` → `*-runner.exe`/`*-runner`
- [ ] Add Linux native build job (optional)
- [ ] Drop Java version from 25 → 21

---

## Known Issues / Decisions Needed

1. **Overlay (on-screen volume display)** — The existing `Overlay.java` (JavaFX window) was deleted with the UI package. A replacement for the overlay has not been designed. Options: keep it as a system tray tooltip, implement it as an always-on-top browser window via a separate Quarkus endpoint, or defer entirely.

2. ~~**`IconResource` / process icons**~~ — **DONE** — `GET /api/icons?path=...` serves PNG icons; `GET /api/processes` returns processes with inline base64 icons. The `icon` field on `AudioSession` is a base64 PNG string (set from JNI on Windows).

3. **OBS re-enablement** — Tracked in Phase 9. A custom Jakarta-compatible OBS WebSocket 5 client can be written against `quarkus-websockets-next` when needed (~200 lines).

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

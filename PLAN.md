# PCPanel — Quarkus Migration Plan

## Overview

The project is a Spring Boot 3 + JavaFX desktop app (283 Java files, 92 beans, 34 command types) that controls PCPanel hardware devices. It uses JNI/JNA native libraries for HID and Windows audio, plus integrations with OBS, MQTT, OSC, VoiceMeeter, and WaveLink. The migration replaces Spring Boot + JavaFX with Quarkus + Angular (via Quinoa), keeps all native library integrations, and adds GraalVM native image support.

---

## Phase 1: pom.xml — Swap Framework

### 1a. Replace Spring Boot parent with Quarkus BOM
- Remove `<parent>` pointing to `spring-boot-starter-parent 3.5.9`
- Add `io.quarkus.platform:quarkus-bom:3.x.x` (latest stable, currently 3.17+) as a `<dependencyManagement>` import
- Keep `<groupId>`, `<artifactId>`, `<version>` as-is

### 1b. Replace Spring dependencies with Quarkus extensions

**Remove:**
- `spring-boot-starter`, `spring-boot-starter-json`
- `javafx-controls`, `javafx-fxml`, `javafx-swing`

**Add Quarkus extensions:**
- `io.quarkus:quarkus-rest` — JAX-RS REST endpoints (the app now has a web server for the Angular SPA)
- `io.quarkus:quarkus-rest-jackson` — Jackson serialization for REST
- `io.quarkus:quarkus-scheduler` — replaces `@EnableScheduling` / `@Scheduled`
- `io.quarkus:quarkus-cache` — replaces `@EnableCaching` / `@Cacheable`
- `io.quarkus:quarkus-websockets-next` — WebSocket server for real-time Angular ↔ backend communication
- `io.quarkiverse.quinoa:quarkus-quinoa` — manages Angular build without manual npm setup
- `io.quarkus:quarkus-config-yaml` (optional) — YAML config support
- `io.quarkus:quarkus-smallrye-health` — optional, nice for native monitoring

**Keep as-is (non-Spring):**
- `jna`, `jna-platform`
- `hid4java`
- `hivemq-mqtt-client`
- `javaosc-core`
- `jnativehook`
- `rxjava`
- `commons-lang3`, `commons-collections4`, `commons-io`
- `streamex`, `lombok`, `batik-transcoder`, `dbus-java-*`, `asm-*`, `jsr305`

### 1c. OBS Integration — disable for now
- The `obs-websocket-community/client 2.0.0` pulls Jetty 9 (`javax.*` namespace) which conflicts with Quarkus's Jakarta EE (`jakarta.*`) expectations and the Quarkus HTTP stack.
- Remove `obs-websocket-community/client`, `jetty-websocket-client`, `jetty-client`, `jetty-http`, `jetty-io`, `jetty-util` from dependencies.
- Comment out / `@Disabled` all OBS service classes and commands (keep the code for future re-enablement with a Jakarta-compatible OBS client).

### 1d. Update build plugins

**Remove:**
- `spring-boot-maven-plugin`
- `javafx-maven-plugin`
- `jtoolprovider-plugin` (jlink/jpackage — no longer needed; native image replaces this)
- `maven-dependency-plugin` copy-dependencies execution
- `maven-jar-plugin` outputDirectory override

**Add:**
- `io.quarkus.platform:quarkus-maven-plugin` — standard Quarkus build plugin (`quarkus:build`, `quarkus:dev`)

**Keep:**
- `maven-compiler-plugin` with Lombok annotation processor
- `os-maven-plugin` extension

### 1e. Add native Maven profile (non-default)

```xml
<profile>
  <id>native</id>
  <properties>
    <quarkus.native.enabled>true</quarkus.native.enabled>
  </properties>
</profile>
```

Normal `mvn package` produces a standard JVM JAR/uber-jar. `mvn package -Pnative` invokes GraalVM native-image.

### 1f. Update application.properties

- Remove `spring.main.*`, `spring.output.*`, `spring.jackson.*`, `spring.main.banner-mode`
- Add `quarkus.http.port=8080` (or a custom port like 7654)
- Add `quarkus.quinoa.package-manager-install=false` (use system npm/node from PATH)
- Add `quarkus.http.host=localhost` (only local access needed)
- Rename version/build/repo keys to `pcpanel.*` namespace, accessed via `@ConfigProperty`
- Keep `linux.commands.*` properties
- Replace `logging.file.name` etc. with Quarkus logging config (`quarkus.log.file.path`, `quarkus.log.file.rotation.*`)
- Add Jackson config: `quarkus.jackson.fail-on-unknown-properties=false`

---

## Phase 2: Main Entry Point & Application Lifecycle

### 2a. Replace Main.java + MainFX.java
- Delete `MainFX.java` (JavaFX bootstrap, Spring context init inside JavaFX `init()`)
- Replace `Main.java` with a Quarkus main class annotated `@QuarkusMain` implementing `QuarkusApplication`
- Move startup logic (`FileChecker`, previously in `Main.main()`) into a `@QuarkusMain` `run()` method or a `@Startup` CDI bean

### 2b. Application lifecycle events
- Replace Spring's `ApplicationContext` start/stop hooks with CDI `@Observes StartupEvent` and `@Observes ShutdownEvent`
- `DeviceScanner` should start scanning on `StartupEvent` rather than relying on `@PostConstruct` Spring wiring
- Graceful shutdown: close HID connections, MQTT, OSC on `ShutdownEvent`

---

## Phase 3: CDI — Replace Spring Annotations Throughout

This is the largest mechanical change (92 beans across 283 files).

### 3a. Bean scope annotations

| Spring | Quarkus/CDI |
|--------|-------------|
| `@Service` | `@ApplicationScoped` |
| `@Component` | `@ApplicationScoped` |
| `@Repository` | `@ApplicationScoped` |
| `@Autowired` | `@Inject` |
| `@Value("${...}")` | `@ConfigProperty(name="...")` |
| `@PostConstruct` | `@PostConstruct` (same, already Jakarta) |
| `@PreDestroy` | `@PreDestroy` (same) |

### 3b. Platform conditionals

Spring's custom `@ConditionalOnWindows`, `@ConditionalOnLinux`, `@ConditionalOnWayland`, `@ConditionalOnPulseAudio` beans need to be replaced.

Options:
- For build-time platform selection (native image): use `@IfBuildProfile("windows")` / Quarkus build-time config or `@io.quarkus.arc.profile.IfBuildProfile`
- For JVM mode (runtime selection): use CDI `@Alternative` pattern — define a qualifier `@Windows` / `@Linux`, produce the appropriate bean via `@Produces` checking `SystemUtils.IS_OS_WINDOWS`, and inject with `@Any Instance<ISndCtrl>`
- **Simplest approach:** Keep interface contracts (`ISndCtrl`, `ITrayService`, etc.) and use `@Produces` factory methods in a config class that checks the OS at runtime

### 3c. Spring Events → CDI Events

| Spring | CDI |
|--------|-----|
| `@EventListener` | `void handle(@Observes MyEvent event)` |
| `ApplicationEventPublisher.publishEvent(e)` | `@Inject Event<MyEvent> bus; bus.fire(e)` |
| Async events | `bus.fireAsync(e)` |

Events to migrate: `AudioSessionEvent`, `AudioDeviceEvent`, `OBSMuteEvent`, `OBSConnectEvent`, device connect/disconnect events.

### 3d. Caching

| Spring | Quarkus |
|--------|---------|
| `@Cacheable` | `@io.quarkus.cache.CacheResult(cacheName="...")` |
| `@CacheEvict` | `@io.quarkus.cache.CacheInvalidate` |
| `CacheManager` config | `application.properties` cache config |

Affected: `IconService`, `CachingConfig`.

### 3e. Scheduling

| Spring | Quarkus |
|--------|---------|
| `@Scheduled(fixedDelay=...)` | `@io.quarkus.scheduler.Scheduled(every="...")` |
| `@Scheduled(fixedRate=...)` | `@Scheduled(every="...")` |

Affected: OBS retry timer, device scan interval (if currently scheduled), VoiceMeeter polling, WaveLink polling.

### 3f. HTTP client
- Replace `RestTemplate` (from `RestTemplateConfig.java`) with Quarkus REST Client (`@RegisterRestClient`) or `java.net.http.HttpClient` for `VersionChecker` and WaveLink HTTP calls.

---

## Phase 4: Remove JavaFX — UI Package

### 4a. Delete all JavaFX-specific code
- Delete entire `src/main/java/com/getpcpanel/ui/` package (all controllers, helpers, dialogs)
- Delete `src/main/java/com/getpcpanel/device/PCPanelProUI.java`, `PCPanelMiniUI.java`, `PCPanelRGBUI.java` (UI-specific subclasses)
- Delete `src/main/resources/assets/*.fxml`, `*.css`, device sub-directories (23 FXML files, 5 CSS files)
- Retain pure model/logic in `Device.java` base class — extract UI-independent state (connected devices, profiles, lighting config) into a non-UI `DeviceHolder` / `DeviceRegistry` service

### 4b. Update TrayService
- `TrayServiceAwt` uses AWT (no JavaFX) — works fine, keep it
- `TrayServiceWayland` uses D-Bus — works fine, keep it
- Just remove any residual JavaFX imports

### 4c. Device model refactoring
- `Device.java` (abstract) currently mixes UI logic and business logic — split into:
  - `DeviceModel.java` — pure data: device type, serial, profiles, lighting state
  - `DeviceService.java` — manages connections, dispatches commands, publishes events
- Device-type specifics (button counts, dial ranges) move to enum or config records

---

## Phase 5: REST API Layer

Create JAX-RS resource classes (`@Path`, `@GET`, `@POST`, etc.) to expose backend services to the Angular frontend.

### 5a. Core resources
- `DeviceResource` (`/api/devices`) — list connected devices, device state
- `ProfileResource` (`/api/profiles`) — list, create, switch, delete profiles
- `CommandResource` (`/api/commands`) — get/set button and dial commands
- `LightingResource` (`/api/lighting`) — get/set lighting config
- `AudioResource` (`/api/audio`) — list audio sessions, devices; set volume, mute
- `SettingsResource` (`/api/settings`) — global settings (OBS disabled for now, VoiceMeeter, WaveLink, MQTT, OSC)
- `IconResource` (`/api/icons`) — return application icons as base64/PNG

### 5b. WebSocket endpoint for real-time updates
Create a `@ServerEndpoint("/ws/devices")` (or Quarkus WebSockets-Next `@WebSocket`) channel for:
- Device connect/disconnect events
- Audio session changes (new app opens, volume changes externally)
- Dial/button press events (for feedback/overlay in the UI)
- Lighting state changes

### 5c. Application process listing
- `ProcessResource` (`/api/processes`) — list running processes with icons (for the process picker)

---

## Phase 6: Angular Frontend (via Quinoa)

### 6a. Quinoa setup
Configure `quarkus-quinoa` in `application.properties`:
```properties
quarkus.quinoa.build-dir=dist/pcpanel
quarkus.quinoa.package-manager=npm
```
Quinoa automatically runs `npm install` and `npm run build` during `mvn package`, serves the Angular SPA from Quarkus HTTP server. The Angular project lives at `src/main/webui/`.

### 6b. Create Angular project
- `ng new pcpanel --directory src/main/webui --routing --style=scss --standalone`
- Use Angular 19+ (latest stable)
- Suggested tech: Angular Material for components, NgRx (or simple services with RxJS) for state, WebSocket service for real-time updates

### 6c. Angular application structure
- `AppComponent` — shell with sidebar navigation
- `DevicesComponent` — device list panel with profile selector dropdown and brightness control
- `DeviceDetailComponent` — device-specific visual (Pro: knobs+sliders, Mini: 4 buttons, RGB: grid of buttons with color)
- `CommandConfigComponent` — modal for configuring button/dial actions (replaces 54 FXML dialogs)
- `LightingComponent` — lighting configuration panel
- `SettingsComponent` — application settings (VoiceMeeter, MQTT, OSC, WaveLink)
- `AudioPickerComponent` — process/device picker for volume commands
- `WebSocketService` — connect to `/ws/devices`, dispatch events to Angular state

### 6d. API communication
- Use Angular `HttpClient` for REST calls
- Use native `WebSocket` or `@stomp/rx-stomp` for real-time events

---

## Phase 7: Native Image Support (GraalVM)

### 7a. Reflection hints
GraalVM native image requires explicit registration of classes used via reflection. In Quarkus, use `@RegisterForReflection` or `reflect-config.json`.

Classes needing registration:
- All 34 command classes (deserialized from JSON by Jackson) — annotate each with `@RegisterForReflection` or add a single `@RegisterForReflection(targets = {CommandMedia.class, CommandRun.class, ...})` on a config class
- JNA interfaces (`SndCtrlNative`, Windows/Linux wrappers) — add to `reflect-config.json`
- `hid4java` internals — may need `--initialize-at-run-time=org.hid4java`
- Jackson polymorphism entries for `ButtonAction`, `DialAction` subtypes

### 7b. Native resource hints
Register resource paths for inclusion in native image:
- Static assets (if any are loaded from classpath at runtime)
- Native DLL/SO files bundled with hid4java are loaded via `System.loadLibrary` — configure `quarkus.native.additional-build-args` to include them

### 7c. JNA in native image
JNA works in native but requires:
- `--initialize-at-run-time=com.sun.jna`
- `--allow-incomplete-classpath` for platform conditionals
- Register JNA callback classes for reflection

### 7d. hid4java in native image
- hid4java bundles HIDAPI native libraries in its JAR — configure `quarkus.native.resources.includes` to pull them in
- The `HidServices` class loads the native library via hid4java's `HidApi` — ensure the native lib extraction path works in native image (needs a writable temp dir)

### 7e. Build config for native
In `application.properties` (or via profile):
```properties
%native.quarkus.native.additional-build-args=\
  --initialize-at-run-time=com.sun.jna,\
  --initialize-at-run-time=org.hid4java,\
  -H:ResourceConfigurationFiles=resource-config.json
```

---

## Phase 8: GitHub Actions Updates

### 8a. Windows native build job
Replace Liberica JDK+FX with Mandrel or GraalVM CE for native image:

```yaml
- uses: graalvm/setup-graalvm@v1
  with:
    java-version: '21'           # Quarkus 3.x targets Java 17/21
    distribution: 'graalvm-community'
    native-image-job-reports: 'true'
- run: mvn -B package -Pnative --file pom.xml
```

Artifact: `target/pcpanel-*-runner.exe` (Quarkus native output on Windows)

### 8b. Linux native build job (optional but recommended)
Similar to Windows but on `ubuntu-latest`, produces `target/pcpanel-*-runner`.

### 8c. Pre-release job
Update artifact paths from `target/*.msi` / `target/*.deb` to `target/*-runner.exe` / `target/*-runner`. For now, no installer wrapping.

### 8d. Java version
Drop from Java 25 to Java 21 (LTS, well-supported by Quarkus 3.x and GraalVM). If Java 25 features are being used, Quarkus 3.15+ supports Java 24.

---

## Phase 9: OBS Integration (Future Re-enablement)

When disabling OBS:
- Comment out `OBS.java`, `ObsConnectedVolumeService.java`
- Comment out `CommandObsMuteSource`, `CommandObsSetSourceVolume`
- Comment out OBS settings in profile JSON model (keep fields for backward compat, just don't use them)
- Add a TODO with the path to re-enable: find/build a Jakarta-compatible OBS WebSocket client (or rewrite the OBS WebSocket 5 protocol directly using Quarkus WebSocket client)

**Re-enablement path (documented for later):**
- OBS WebSocket protocol 5 is well-documented
- Quarkus provides `quarkus-websockets-next` which is a Jakarta client — a custom OBS client class replacing the community library can be written directly against this in ~200 lines

---

## Migration Order (Suggested Sequencing)

1. `pom.xml` — framework swap, dependency cleanup (this unblocks everything)
2. `application.properties` — rename keys to Quarkus equivalents
3. `Main.java` — replace with `@QuarkusMain`
4. Spring→CDI annotation sweep — mechanical find-and-replace across all 283 files
5. Platform conditionals — rewrite `@ConditionalOn*` as CDI `@Produces` / `@Alternative`
6. Events — replace `ApplicationEventPublisher` with CDI `Event<T>`
7. Caching/Scheduling — swap annotations
8. HTTP client — replace `RestTemplate`
9. OBS — disable, remove conflicting deps
10. JavaFX removal — delete UI package, extract business logic from Device classes
11. REST API layer — create resource classes
12. WebSocket endpoint — real-time event bridge
13. Angular project creation — scaffold + basic routing
14. Angular components — replicate each UI screen
15. Native image hints — register reflection/resources
16. GitHub Actions update — GraalVM builds
17. Test — validate JVM mode works, then native mode

---

## Key Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| JNA in GraalVM native | Well-documented, use `--initialize-at-run-time`, register JNA callbacks; hid4java has GraalVM support notes |
| Command JSON polymorphism (34 types) | `@RegisterForReflection` on all command classes; verify Jackson `@JsonSubTypes` works |
| Platform conditionals at native build time | For native, platform is known at build time → use `@IfBuildProfile`; for JVM keep runtime detection |
| SndCtrl DLL loading in native | DLL is a separate file loaded via `System.load(path)` — works in native; just ensure the DLL ships alongside the executable |
| `javax.activation` dependency | Remove it (already pulled by OBS); if needed elsewhere use Jakarta equivalent |
| Spring-specific test annotations | Replace with `@QuarkusTest` in test classes |
| Overlay notification (system tray popup) | AWT-based `TrayServiceAwt` works independently of JavaFX; keep as-is |
| Profile auto-switch by focused app | Uses JNA `getFocusApplication()` — works in native; no change needed |

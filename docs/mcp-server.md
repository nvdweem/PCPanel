# PCPanel MCP server (dev-only)

A [Model Context Protocol](https://modelcontextprotocol.io) server that exposes the **running app's
runtime state** and a **hardware-free test harness** to an agent — so you can introspect and validate
the app without HTTP-probing `/q/health`, grepping `logging.log`, or owning physical
PCPanel/Deej/MIDI hardware.

It is a **developer affordance**, gated off by default and never present in the shipped build. See
the build spec rationale in [`device-layer-generalization-plan.md`](device-layer-generalization-plan.md).

## TL;DR for agents

The tools are reachable **three ways** on the app's existing localhost server (`127.0.0.1:7654`):

1. **Plain REST** under `/api/mcp/*` — no MCP client, no protocol, no handshake. Just `curl`. **Use
   this if you are unsure** (e.g. you just started the app yourself and want to poke it):
   ```bash
   curl -s 127.0.0.1:7654/api/mcp/runtime-info | jq
   curl -s 127.0.0.1:7654/api/mcp                      # lists every endpoint
   ```
2. **MCP Streamable HTTP** at `POST /mcp` — the standard MCP transport, a single HTTP endpoint
   (JSON-RPC: `initialize` → `notifications/initialized` → `tools/list` / `tools/call`, carrying the
   returned `Mcp-Session-Id` header). Register `http://127.0.0.1:7654/mcp` as a Streamable-HTTP MCP
   server in your client.
3. **MCP over SSE** at `/mcp/sse` — the older SSE transport, for clients that only speak SSE.

All three surfaces call the same code. The plain-REST one (1) exists precisely because connecting an
MCP client to an app you just launched can be awkward — when in doubt, `curl /api/mcp/*`.

## Enabling it

Nothing is built or wired unless you ask for it, at **two** levels:

| Gate | Default | How to turn on | What it controls |
| --- | --- | --- | --- |
| Maven profile `mcp` (`-Dpcpanel.mcp=true`) | off | pass the flag on the build | Compiles `src/mcp/java` and adds the `quarkus-mcp-server-sse` dependency. Without it, **nothing** MCP exists in the build — the default/native/CI build is untouched. |
| Build-time flag `pcpanel.mcp.dev` | `false` (`%dev` → `true`) | `quarkus:dev` turns it on automatically | Wires the **dev** tools (introspection, simulation, virtual devices). Future *non*-dev tools will omit this gate. |

The normal way to run it (dev mode turns the `dev` flag on for you):

```bash
./mvnw quarkus:dev -Dpcpanel.mcp=true
# backend on :7654, MCP at /mcp/sse + /api/mcp, Angular on :4200
```

To exercise it without the Angular frontend (faster), add `-Dquarkus.quinoa.enabled=false`.

For a packaged JVM jar that includes the tools, the `dev` flag must be set **at build time** (it is a
build-time property), e.g.:

```bash
./mvnw package -Dpcpanel.mcp=true -Dpcpanel.mcp.dev=true -Dquarkus.native.enabled=false
```

The default build (`./mvnw package`, native, CI) passes neither flag, so the server is absent — no
dependency, no endpoint, nothing reaches end users.

## Tools

### Read-only introspection
| Tool / REST | What it returns |
| --- | --- |
| `pcpanel_runtime_info` · `GET /api/mcp/runtime-info` | version, build (`native`\|`jvm`), OS, data root, HTTP port, loaded device providers, integration status (OBS/WaveLink/Voicemeeter/MQTT). |
| `pcpanel_list_devices` · `GET /api/mcp/devices` | live + persisted-offline devices, each with its capability descriptor. |
| `pcpanel_get_device` · `GET /api/mcp/devices/{serial}` | full snapshot: descriptor, current profile + assignments, live analog values, lighting, visual colors. |
| `pcpanel_debug_resolve` · `GET /api/mcp/debug/resolve/{serial}` | the **resolved** per-control view of the base-layer + stepped-switch logic: per control, which profile its command and lighting came from (`active`\|`baseLayer`\|`none`), and a stepped switch's (`CommandAnalogBands`) live selected band + feedback colour. Works for offline persisted devices too — use it to assert base-layer fallback and stepped-switch transitions after `simulate_analog`/`_button`. |
| `pcpanel_list_serial_ports` · `GET /api/mcp/serial-ports` | serial ports; `available:false` + error string instead of throwing. |
| `pcpanel_list_midi_devices` · `GET /api/mcp/midi-devices` | MIDI inputs; `midiSubsystemAvailable:false` + note makes the "empty in native image" case explicit. |

### Logs / errors
| Tool / REST | What it returns |
| --- | --- |
| `pcpanel_recent_logs` · `GET /api/mcp/logs?level=&limit=&contains=` | tail of `${pcpanel.root}/logs/logging.log`, filtered + capped. |
| `pcpanel_last_error` · `GET /api/mcp/last-error` | most recent ERROR/exception with stack trace, from an in-memory ring buffer (works even if file logging is off). |

### Test harness — synthetic input + virtual devices + effects
| Tool / REST | What it does |
| --- | --- |
| `pcpanel_simulate_analog` · `POST /api/mcp/simulate/analog` | fire the same `KnobRotateEvent` the input layer fires (value in the canonical **0–255** domain). |
| `pcpanel_simulate_button` · `POST /api/mcp/simulate/button` | fire `ButtonPressEvent` (a press also runs the configured click action). |
| `pcpanel_simulate_deej_line` · `POST /api/mcp/simulate/deej` | run a raw Deej line (`"0\|512\|1023"`) through the real `DeejProtocol` parse + normalize, then fire per slider. |
| `pcpanel_simulate_midi` · `POST /api/mcp/simulate/midi` | run a raw MIDI message through `MidiProtocol` decode + normalize, then fire. |
| `pcpanel_simulate_wavelink_mute` · `POST /api/mcp/simulate/wavelink-mute` | inject a Wave Link channel state (incl. mute) through the real listener path; drives the mute-override colour for a control bound to that channel. `channelId` must match the bound command's `id1`. |
| `pcpanel_simulate_obs_mute` · `POST /api/mcp/simulate/obs-mute` | report an OBS source as muted/unmuted and fire `OBSMuteEvent` through the real path, without a live OBS connection; drives the mute-override colour for a control bound to that OBS source (volume dial or mute button). |
| `pcpanel_create_virtual_device` · `POST /api/mcp/virtual-device` | register a synthetic device from a `DeviceDescriptor` JSON with no hardware (use a non-`pcpanel` providerId + null `globalLighting`, e.g. a fake Deej). It appears in `list_devices` and renders in the UI. |
| `pcpanel_remove_virtual_device` · `DELETE /api/mcp/virtual-device/{serial}` | disconnect it and purge its persisted entry. |
| `pcpanel_get_audio_state` · `GET /api/mcp/audio-state?filter=` | per-device + per-process volume/mute, defaults, focused app. |
| `pcpanel_focus_volume_target` · `GET /api/mcp/focus-volume-target?application=` | inspect the focused-app volume **deferral decision**: the redirector that would claim a given app's focus volume (e.g. `WaveLinkService`) or null when the OS controls it. Side-effect-free — does not change any volume. Use it to assert focus volume defers to Wave Link for a Wave-Link-managed app and hits the OS otherwise. |

Event dispatch is async in places, so the simulation/virtual-device tools return promptly with an
ack — **poll** `pcpanel_get_audio_state` / `pcpanel_get_device` for the effect rather than expecting a
synchronous result.

## End-to-end example (no hardware)

```bash
BASE=127.0.0.1:7654/api/mcp
# 1. create a 4-slider fake Deej
curl -s -XPOST $BASE/virtual-device -H 'content-type: application/json' -d '{
  "serial":"deej:virtual",
  "descriptor":{
    "providerId":"deej","deviceKindId":"deej","displayName":"Virtual Deej",
    "analogInputs":[
      {"index":0,"id":"slider0","label":"S1","kind":"SLIDER","sourceMin":0,"sourceMax":1023,"hasButton":false,"lightOutputIndex":null},
      {"index":1,"id":"slider1","label":"S2","kind":"SLIDER","sourceMin":0,"sourceMax":1023,"hasButton":false,"lightOutputIndex":null},
      {"index":2,"id":"slider2","label":"S3","kind":"SLIDER","sourceMin":0,"sourceMax":1023,"hasButton":false,"lightOutputIndex":null},
      {"index":3,"id":"slider3","label":"S4","kind":"SLIDER","sourceMin":0,"sourceMax":1023,"hasButton":false,"lightOutputIndex":null}],
    "digitalInputs":[],"lightOutputs":[],"analogOutputs":[],"globalLighting":null}}'
# 2. it now shows up (connected:true) and renders in the UI
curl -s $BASE/devices | jq '.[] | select(.serial=="deej:virtual")'
# 3. bind slider 0 to a process volume in the UI, then drive it to ~50%
curl -s -XPOST $BASE/simulate/analog -H 'content-type: application/json' -d '{"serial":"deej:virtual","index":0,"value":128}'
# 4. observe the effect
curl -s "$BASE/audio-state?filter=chrome" | jq
# 5. clean up
curl -s -XDELETE $BASE/virtual-device/deej:virtual
```

## Security

Localhost-only (the app's HTTP server binds `127.0.0.1`). Gated off by default at the Maven level, so
the shipped native build contains no MCP code, no extension, and no endpoint. Never enable it in a
build intended for end users.

The startup log emits `CORS filter must be enabled for Streamable HTTP MCP server endpoints` — this is
harmless here: agents call these endpoints with `curl`/a native MCP client (not a browser), so CORS is
not involved. Only enable `quarkus.http.cors.enabled=true` if you specifically need to drive the
Streamable HTTP transport from a web page.

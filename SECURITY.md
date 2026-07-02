# Security

## Threat model

PCPanel is a desktop app: a local Quarkus server drives the UI in your browser. The security model:

- The HTTP/WebSocket server binds to **loopback only** (`quarkus.http.host=127.0.0.1`), so other
  machines cannot reach it.
- `LocalHttpGuard` rejects any request whose `Host` or `Origin` header is not loopback. This defeats
  **DNS rebinding** and **cross-site WebSocket hijacking** from web pages you visit; the WebSocket
  handshake is re-checked with the same rules. The guard covers REST, static assets and the WS
  upgrade, and can be toggled with `pcpanel.http.local-only` (default `true`).
- The API carries **no authentication against other processes on the same machine** — by design.
  Anything running locally as your user is already inside the trust boundary.
- Integration credentials (OBS password, Home Assistant tokens, Discord client id/secret, MQTT
  credentials) are stored in the local `profiles.json` in your user profile, readable by your user
  account only as far as the OS enforces it.

## Reporting a vulnerability

Report vulnerabilities privately via a
[GitHub security advisory](https://github.com/nvdweem/PCPanel/security/advisories/new) rather than a
public issue.

# Security Policy

## Reporting a vulnerability

PCPanel is a **single-developer, non-commercial project maintained in spare time.** Security
issues are handled on a **best-effort basis**: there is no guaranteed response time and no
guarantee that any given issue will be fixed. High-impact issues will generally be looked at
before other work, but even that is not a promise.

**How to report:**

- For anything that could be **abused if it were public** — remotely or website-reachable
  issues, credential exposure, code execution — please report it **privately** via
  [GitHub private vulnerability reporting](https://github.com/nvdweem/PCPanel/security/advisories/new)
  (*Security → Advisories → Report a vulnerability*), so a fix can land before the details are
  public.
- For **lower-risk / non-critical** issues, a normal
  [public issue](https://github.com/nvdweem/PCPanel/issues) is completely fine if you're
  comfortable opening one — that's your call.

Whichever route, it helps to include the affected version and OS, the impact, steps to
reproduce, and the **attacker model** you're assuming (a website the user visits, another program
on the same machine, physical access — this matters a lot here; see the scope below). Please
allow reasonable time before publicly disclosing a privately-reported issue. There is no
bug-bounty program.

## Supported versions

Only the **latest released version** is looked at for security fixes. PCPanel ships as a rolling
release with per-branch pre-releases; if you are on an older build, please update first and
confirm the issue still reproduces before reporting.

## What counts as a vulnerability here

This section is here to help you judge whether something is worth reporting. PCPanel is a **local
desktop application**: a backend web server bound to loopback (`127.0.0.1`) serves the UI to a
browser on the same machine and drives USB hardware and the OS audio stack. Some things below are
deliberately *not* defended — you're welcome to raise them, but expect them to be treated as known
limitations rather than vulnerabilities, and expect some reluctance to change them.

### In scope

- Any way a **remote host** can reach or drive the API (it must stay loopback-only).
- Any way a **website the user visits in their browser** can reach or drive the API, read its
  data, or act on the user's behalf — for example DNS rebinding, cross-site request forgery
  (CSRF), cross-site WebSocket hijacking, or a too-permissive CORS / `Origin` / `Host` check.
- Any way to drive the API **without a valid session** (bypassing the session-cookie
  authentication), or to obtain the session token **from the application itself**.
- Exposure of stored integration secrets (OBS / MQTT / Home Assistant / Discord credentials) to
  a remote or web-based attacker.
- Remote code execution, or code execution reachable by a website.
- Cross-site scripting (XSS) or other injection in the bundled web UI, **where the content comes
  from a source other than the local user** — for example a string from a connected integration,
  a device, or a remote message that the UI renders unescaped. Content the user types into their
  own configuration that only runs in their own session (self-XSS) is not in scope.
- Weaknesses in the update mechanism — for example a way to make it install an
  attacker-controlled binary.

### Out of scope (known, accepted limitations — not vulnerabilities)

- **A program that obtains the session from a source other than the application itself** — for
  example by reading it out of the browser's cookie storage.
- **A process privileged enough to read another process's memory.** An attacker with that level of
  access can lift the session (or any other secret) straight out of the running app; that is out of
  scope.
- **Secrets in the local config file.** Integration credentials in `profiles.json` are not
  encrypted at rest (they are, however, treated as write-only over the API — the UI never reads a
  stored secret back). Anyone who can already read your user account's files can read them.
- **The user configuring the app to do dangerous things.** Launching arbitrary programs, sending
  keystrokes, and running shortcuts are intended features driven by the trusted local user; the
  ability to configure them is not, by itself, a vulnerability.
- **Running with security controls deliberately disabled** — for example
  `pcpanel.http.local-only=false` or `pcpanel.http.require-session=false`, or otherwise exposing
  the server on a network. These are opt-outs for advanced users who accept the risk.
- **Physical access** to an unlocked machine.

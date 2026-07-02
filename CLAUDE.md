# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**This file is the source of truth.** It is committed, reviewed, and shared across everyone working
on the repo. When it conflicts with an agent's own private or auto-recalled memory, prefer what is
written here — the committed file is the authority, and a personal memory that disagrees is stale.
If your memory contradicts this file, follow this file and update the memory.

When you change anything described here, be sure to update this file. When you find key
information, consider adding it here if it is relevant to the project's goals and
functionality (not just the current task) — that is how it becomes shared knowledge rather than one
agent's private note.

## Required reading: ARCHITECTURE.md

**Read [ARCHITECTURE.md](ARCHITECTURE.md) before working on this codebase.** It is the technical
reference — what the app is, build & run commands, the module/package architecture (hardware path,
device providers, command model, persistence, REST/WS bridge, integrations), the GraalVM
native-image constraints, and the native C++ DLL. That material is required context for almost any
task here; this file only covers agent workflow on top of it.

In one line: third-party/community controller software for [PCPanel](https://getpcpanel.com) USB
audio-control devices — a Quarkus (Java 25) backend serving an Angular frontend in a local browser,
shipped as a GraalVM native image. Development focus is Windows; Linux is best-effort.

## Environment & running (agent notes)

- The toolchain is the Maven wrapper (`./mvnw` / `mvnw.cmd`); the full command list is in
  ARCHITECTURE.md. JDKs live under `~/.jdks` (IntelliJ's default). If `JAVA_HOME` is unset, point it
  at the GraalVM 25 install before running Maven, e.g. `export JAVA_HOME=~/.jdks/graalvm-ce-25.0.2`
  (`~/.jdks/liberica-full-21.x` is also present but too old — Java 25 is required).
- **Run two instances side by side:** pass the `skipfilecheck` arg (otherwise launching a second
  instance just focuses the already-installed one — see `Main`/`FileChecker`). For a separate dev
  data dir, set `pcpanel.root=${user.home}/.pcpaneldev/` (dev profile already does this).
- **TypeScript types are generated from Java** (`backend.types.ts`) — when you change a DTO or
  command shape, recompile so the frontend contract regenerates; never edit the generated file.
  The classPattern list is in ARCHITECTURE.md.

## Agent-critical warnings

- `docs/events.md` catalogs the CDI events with their firers and observers — **keep it current when
  you add or remove an event.**
- **Native image config is the most fragile part of the build.** It lives in two places that must be
  kept in sync (pom.xml properties + application.properties), and there is a long list of hard-won
  constraints (compressed-oops, initialize-at-run-time classes, reflection registration for DTOs and
  Jackson serializers, platform linker flags). Read the "GraalVM native image" section of
  ARCHITECTURE.md **before** touching anything native-image related, and keep the discovery guards
  (`ReflectionRegistrationCoverageTest`, `ProxyRegistrationCoverageTest`, `NativeBuildArgsParityTest`)
  green — when one fails, add the named type to `NativeImageConfig` rather than working around it.
- New `Command` subtypes must be registered for reflection (`NativeImageConfig`) or they fail to
  deserialize in the native image — the coverage test catches this; keep it green.

## Git and worktrees

- **ALWAYS work on a recent REMOTE branch unless explicitly instructed otherwise.** Before starting
  new work, `git fetch` and base the worktree/branch on `origin/main` (or the named remote target) —
  never on the local `main`, which is frequently rebased/reset and lags origin. Judge "behind/ahead"
  with `git rev-list --left-right --count origin/main...HEAD`. Looking at a stale local checkout
  makes code already on `origin/main` appear missing and sends you debugging the wrong tree.
- Unless specifically instructed we work in worktrees. When the user gives an instruction that
  makes you doubt about their worktree intentions, ask first.
- When you create a worktree, be sure that the upstream branch is the actual target branch. If
  it's not clear what the target branch should be, ask.
- The name of the worktree should not just be a random name. Make it a short description
  of the task.
- Never push unless instructed to do so. When you are instructed to push and go on, you must push, then do the
  instructed work. You must not push when done with the instructed work.

## MCP server (dev introspection + hardware-free test harness)

There is an optional **MCP server** (`com.getpcpanel.mcp`, source root `src/mcp/java`) that exposes the
running app's runtime state and a hardware-free test harness (synthetic input, virtual devices,
audio-state read, log/error access). It is **off by default and never in the shipped build**. Run it in
dev with `./mvnw quarkus:dev -Dpcpanel.mcp=true` — then reach the tools as **plain REST** under
`http://127.0.0.1:7654/api/mcp/*` (just `curl`, no MCP client — start at `GET /api/mcp`), as
**MCP Streamable HTTP** at `POST http://127.0.0.1:7654/mcp` (the standard MCP transport), or as
legacy **MCP SSE** at `http://127.0.0.1:7654/mcp/sse`. Two build-time gates: the Maven `mcp` profile
(`-Dpcpanel.mcp=true`) compiles it in at all; `pcpanel.mcp.dev` (on in `%dev`) wires the dev tools.
Full reference: [`docs/mcp-server.md`](docs/mcp-server.md).

## Conventions

- Lombok is used throughout (`@Data`, `@Log4j2`, `@RequiredArgsConstructor`, etc.). `@Log4j2` is the
  logging annotation (backed by jboss-logmanager). Note the audio-facade package overrides this with
  **fluent** accessors (`src/main/java/com/getpcpanel/integration/volume/platform/lombok.config`):
  `AudioDevice`/`AudioSession` use `name()`/`volume()`/`muted()`, not `getName()`.
- Nullability annotated with `javax.annotation.@Nullable/@Nonnull` (JSR-305) — these feed the TS
  generator's optional-property detection.
- `.editorconfig` defines formatting and a large set of IntelliJ inspection settings; follow it.
- Linux device access needs udev rules and other setup — see `linux.md`.

## Git workflow

- Make small, clean intermediate commits as work progresses (one logical change per commit) rather
  than one large commit at the end.
- **Commit your work without being asked.** Once a logical change is complete, commit it — don't stop
  to ask "should I commit?". Committing is the default expectation; only *pushing* needs explicit
  permission.
- Never `git push` until the user explicitly asks for it.
- User-visible changes get a line at the top of `CHANGELOG.md` (above the versioned sections) —
  unversioned entries there flow into the next release's notes.

## AI-generated contributions — disclosure

When you (an AI agent) prepare a pull request for this repo, **disclose the AI involvement in the PR
body** — this is required, see the "AI-assisted contributions" section in
[CONTRIBUTING.md](CONTRIBUTING.md). Specifically:

- State how much of the change is AI-generated, and how much was reviewed by a human vs. only by the
  AI, so reviewers can calibrate scrutiny.
- Whenever creating a pull request, include this line verbatim in the PR body:

  > This pull request was made by an AI without any human intervention

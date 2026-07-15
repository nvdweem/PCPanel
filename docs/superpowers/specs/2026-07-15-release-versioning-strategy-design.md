# Release versioning strategy — SemVer pre-release precedence

**Status:** approved 2026-07-15. **Goal:** let stable releases be clean `.0` versions
(`2.0`, `2.1`, `3.0`) that a push-button CI produces, without the "`2.0` is older than
`2.0.x`" strangeness that breaks version comparison (notably the Windows auto-updater).

## Problem

Today every build is `<baseversion>.<GITHUB_RUN_NUMBER>` (e.g. `2.0.83`) and the comparator
(`Version.SemVer`) appends the build number as an extra **numeric core part**, then does
"longer list wins on equal prefix". So `2.0` → `[2,0]` and `2.0.83` → `[2,0,83]`, and
`[2,0] < [2,0,83]`. A bare `2.0` release therefore sorts **below** every `2.0.x` snapshot.
Both update paths use this comparator — `VersionChecker` (the "update available?" check) and
`WindowsInstallerUpdater.selectLatest` (which release the Windows installer picks) — so a real
`2.0` would never be offered to anyone on a `2.0.x` build, and snapshots read as newer than the
release. See the current (backwards) `VersionTest`: `1.7 < 1.7-snapshot < 1.7.1 < …`.

## Strategy

Treat a snapshot as a **pre-release of the version it leads to**, per SemVer 2.0.0 precedence
(`2.1.0-snapshot.5 < 2.1.0`). Every ordering then becomes sane, with clean `.0` releases:

```
2.0-SNAPSHOT (82)  <  2.0-SNAPSHOT (90)  <  2.0  <  2.1-SNAPSHOT (1)  <  2.1-SNAPSHOT (5)  <  2.1
```

### Version formats

| Build              | `pcpanel.version`          | GitHub release name      | Parsed              | Prerelease?          |
|--------------------|----------------------------|--------------------------|---------------------|----------------------|
| Dev snapshot (main)| `2.1-SNAPSHOT` (+ build N) | `v2.1-SNAPSHOT (N)`      | core `[2,1]`, pre N | yes — rolling `latest-main` |
| **Stable release** | `2.1` (`-SNAPSHOT` stripped)| `v2.1`                  | core `[2,1]`, final | **no — marked Latest** |

Snapshot naming is unchanged from today. The only new artifact shape is the **stable build**,
which strips `-SNAPSHOT` (so `pcpanel.version` is bare, `currentIsSnapshot == false`) and is
published non-prerelease under a permanent `vX.Y` tag.

### Branch / CI model (push-button)

- `main` always carries `<next>-SNAPSHOT` (today: `2.0-SNAPSHOT` = "developing 2.0").
- **Release = push `releases/X.Y`.** CI strips `-SNAPSHOT` (edits `<project.snapshot>` to empty
  in the CI checkout), builds bare `X.Y`, and publishes it non-prerelease as Latest, tag `vX.Y`.
  No manual GitHub editing.
- After releasing, advance dev with one line: `packaging/bump-version.sh 2.1` (a deliberate
  "what's next" choice). No CI auto-bump — kept explicit.

## Code changes (3 contained pieces)

1. **`Version.SemVer`** — rewrite `compareTo` to SemVer precedence: compare numeric core
   (missing components padded with 0); equal core ⇒ *final outranks pre-release*; both
   pre-release ⇒ compare build number. `SemVer` gains an explicit pre-release flag + build; a
   `-SNAPSHOT`/non-empty suffix marks a pre-release, `(N)` supplies the build. `withBuild(n)`
   sets the pre-release build instead of appending a core part. Parsing keeps the existing
   `v?([\d.]+)(-\S*)?` + `(\d+)` regexes.
2. **`.github/workflows/build-and-release.yml`** — on `releases/**`: strip `-SNAPSHOT` before
   building, and publish a **non-prerelease** `vX.Y` (permanent tag, marked `--latest`, no
   "(N)"). Snapshot branches keep the rolling `latest-<branch>` prerelease exactly as today.
3. **`VersionTest`** — flip expectations to the sane order:
   `1.7-snapshot < 1.7 < 1.7.1-snapshot < 1.7.1 < 1.8-snapshot < 1.8`, plus cross-version cases
   (`2.0-SNAPSHOT(83) < 2.0 < 2.1-SNAPSHOT(1)`, build-number ordering within a pre-release,
   mixed-length cores).

## One-time transition

The new comparator ships *inside* 2.0, so already-deployed apps still run the old one. In
practice the switch is smooth: current **stable** users (1.7.1) see `2.0 > 1.7.1` and
auto-update; current **snapshot** users auto-update to the next `2.1-SNAPSHOT` build (the old
comparator still sees `2.1.x > 2.0.x`) and pick up the new logic there. The only gap is a
snapshot user wanting to drop *back* to stable 2.0 at release moment — a one-time manual install
for a handful of testers. Acceptable, and only happens once.

## Rejected alternatives

- **Keep build-number scheme, make the release the highest `2.0.<N>`** — reintroduces the
  non-clean versions we explicitly want gone.
- **Fully separate stable/snapshot version namespaces** — more machinery, no benefit over the
  SemVer approach.

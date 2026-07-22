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

> **Superseded (2026-07-22): release = push a `vX.Y.Z` tag, not a `releases/**` branch.**
> The model above put the release version in the tree (`<project.baseversion>` + the AppStream
> `<release>` entry). That works for a single line, but it makes a long-lived maintenance branch
> unmergeable in practice: `releases/2.0` and `main` both edit the same version line from a common
> ancestor, so **every** forward merge conflicts on `pom.xml` and the metainfo — and resolving it once
> does not help, because the merge base advances to a release-branch tip that still disagrees
> (measured: merge #2 conflicts identically after merge #1 was resolved).
>
> Now: the tag carries the version and nothing in the tree does. `packaging/ci-version.sh` (one
> implementation, called by all four jobs, guarded by `CiVersionScriptTest`) maps
> `refs/tags/vX.Y.Z` → build `X.Y.Z` via `-Dproject.baseversion=X.Y.Z -Dproject.snapshot=`; every other
> ref → snapshot `<baseversion>.<run>`. A `releases/**` push now publishes that line's rolling
> *snapshot* pre-release so a fix can be tested before it is released. `releases/2.0` never edits a
> versioned file, so it merges forward into `main` with only real-code conflicts. The AppStream
> `<release>` entry is stamped at package time (`packaging/linux/stamp-metainfo.sh`), and *Latest* is
> claimed only by the highest released version so a `2.0.x` patch cut after `2.1` cannot demote it.
>
> Everything below about **version formats and SemVer precedence is unchanged** — `2.0-SNAPSHOT (90)
> < 2.0 < 2.1-SNAPSHOT (1)` still holds, and the `2.0.84` one-time transition still applies.

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

The new comparator ships *inside* the first release, so already-deployed apps still run the old
one, which ranks a build number *above* the bare version (`2.0.83 > 2.0`). To reach those
installs, cut the **first** stable release with a build number one above the last snapshot — e.g.
last `latest-main` is `(83)`, so release **`2.0.84`** (`packaging/bump-version.sh 2.0.84`), bare
and non-prerelease. Then:

- Old **stable** users (1.7.1): old comparator sees `2.0.84 > 1.7.1` → auto-update. ✓
- Old **snapshot** users (2.0.83): old comparator parses `v2.0.84` as `[2,0,84]` (no parenthesised
  build to append) and sees `[2,0,84] > [2,0,83]` → auto-update straight to the stable release. ✓

That `2.0.84` build carries the new comparator, so from then on ordering follows SemVer precedence
and the *next* release is a clean `2.1` (`[2,1] > [2,0,84]`). The only one-time cost — accepted —
is that this first release is `2.0.84` rather than a clean `2.0`; every release after is clean
`.0`. Bump `main` to `2.1-SNAPSHOT` immediately after so no further `2.0.x` snapshots appear.
`VersionTest.oneTimeTransitionReleaseOrdersConsistently` guards the new-comparator side of this.

## Rejected alternatives

- **Keep build-number scheme, make the release the highest `2.0.<N>`** — reintroduces the
  non-clean versions we explicitly want gone.
- **Fully separate stable/snapshot version namespaces** — more machinery, no benefit over the
  SemVer approach.

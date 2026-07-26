# Linux self-update channels

## Problem

The Flatpak has two update channels — the OSTree refs `stable` and `snapshot` — and an install follows
the ref it came from. The AppImage has no equivalent: its zsync target is baked into the file at build
time, and a snapshot build bakes `latest-<branch>`, so AppImage self-update follows *one branch's*
rolling pre-release rather than a channel.

Neither format moves a snapshot install onto a stable release when one ships. A user on the snapshot
channel stays on snapshots forever, even while the newest thing that exists is a final release.

## Design

Two channels, both formats, ordered by *publication*, not by version:

- **`latest-snapshot`** — the most recent build of the development line.
- **`latest`** — the most recent stable release.

A stable release publishes to **both**. That is the whole rule; the three cases the channels must
satisfy fall out of it with no version comparison anywhere:

| State | `latest` | `latest-snapshot` |
|---|---|---|
| `v1` released, `v2` snapshot built after it | `v1` | `v2` snapshot |
| `v2` released after its own snapshots | `v2` | `v2` |
| `v3` snapshot built after `v2` released | `v2` | `v3` snapshot |

### Why no version comparison is needed

The branch's `<project.baseversion>` is bumped to the next patch immediately after each release
(`chore: develop 2.0.89` follows tag `v2.0.88`), so a snapshot is always titled with the version it
leads to and always outranks the newest tag. Publication order and SemVer order therefore coincide,
and `VersionChecker`'s "highest SemVer wins" prompt can never disagree with what the channel serves.

`Version.SemVer`'s guarded `2.0-SNAPSHOT (90) < 2.0` precedence stays exactly as it is. It is the
correct rule; it simply describes a state this workflow does not produce, because a released `2.0.89`
is never followed by another `2.0.89-SNAPSHOT`.

### Publication matrix

| Ref built | Release assets go to | Baked into the AppImage |
|---|---|---|
| `refs/tags/v*` | permanent `vX.Y.Z` release | `latest` |
| `refs/tags/v*` | *and* `latest-snapshot` | `latest-snapshot` |
| dispatch of `main` or `releases/**` | `latest-snapshot` | `latest-snapshot` |
| dispatch of any other branch | `latest-<branch>` | `latest-<branch>` |

The release channel needs no new tag or release object. `latest` is a magic value in
`appimageupdatetool` (verified in AppImageUpdate's `GithubReleasesZsyncUpdateInformation.cpp`,
matched exactly, alongside `latest-pre` and `latest-all`) that resolves to GitHub's newest
non-prerelease — which is the release channel. Any other string, including `latest-snapshot`, is
treated as a literal tag.

The existing rule that a tag which is *not* the newest bakes its own permanent tag instead of `latest`
is unchanged: it is what stops a `2.0.86` cut after `2.1` shipped from redirecting its users onto the
newer line.

Feature branches keep their own `latest-<branch>` pre-release so a test build cannot take over the
channel that real users follow.

### AppImage: two builds per release

An AppImage's zsync target is baked into the file, so a release build runs `build-appimage.sh` twice —
identical contents, differing only in the embedded update-information string:

- `UPDATE_INFO=…|latest|…` → uploaded to the permanent `vX.Y.Z` release.
- `UPDATE_INFO=…|latest-snapshot|…` → uploaded to the `latest-snapshot` pre-release.

Without the second copy a snapshot user pulled onto the release build would receive a file with
`latest` baked in and become a stable-channel user permanently, so the third case above would never
reach them again. With it, they run the final build while still following the snapshot channel.

### Flatpak

A ref *is* the channel, so no second build is needed. A release build commits to `stable` as it does
today, then `flatpak build-commit-from` copies that same commit onto the `snapshot` ref — object
deduplicated, no rebuild. A snapshot install stays on the `snapshot` ref and picks development back up
on the next dispatch, matching the AppImage exactly.

### Concurrency

`concurrency: release-${{ github.ref }}` no longer prevents a race now that `main` and `releases/**`
publish to the same tag. The group becomes channel-keyed for dev-line builds so those serialise, while
feature branches stay keyed on their own ref.

## Out of scope

- `VersionChecker`'s `releases?per_page=4` window. Orthogonal to channels and unchanged by this work.
- Windows and `.deb`, which have no channel concept: Windows resolves its target from the releases API
  by SemVer, and `.deb` has no self-update.

## Documentation

- `CLAUDE.md`'s self-update section gains the channel model. Its snapshot-versioning example is stale
  against the pom (it shows `pcpanel.version = <baseversion>-SNAPSHOT` with a bare `2.0` baseversion;
  the pom carries the full next patch, `2.0.89`) and is corrected.
- `CHANGELOG.md` top section: snapshot installs now move to a stable release when one ships, and
  return to snapshots when development moves ahead.

## Verification

The magic-tag resolution is verified against the AppImageUpdate source. End-to-end channel behaviour
can only be confirmed by real CI runs, which the maintainer triggers: dispatch a dev-line snapshot,
tag a release, dispatch again, and check what `latest-snapshot` carries at each step.

# Pending CI workflow changes

`pending-ci-workflows.patch` in this directory holds workflow changes that could
not be pushed from the automated branch because the push credential lacked the
GitHub `workflow` OAuth scope (GitHub rejects any push whose history modifies
`.github/workflows/**` without it).

Contents of the patch:
- **`.github/workflows/pr-ci.yml`** (new) — runs the JVM test suite (incl. the
  GraalVM registration guards) and the frontend build on Windows + Linux for
  pull requests. Gated to same-repo PRs only, so fork PRs never consume build
  minutes.
- **`build-and-release.yml` / `build-sndctrl-dll.yml`** — every action pinned to
  a commit SHA (tag in a trailing comment) and `appimagetool` pinned to the
  tagged 1.9.1 release with a sha256 integrity check.

Apply from the repo root with a `workflow`-scoped token, then delete this note:

    git apply .github/pending-ci-workflows.patch
    git rm .github/PENDING-CI-WORKFLOWS.md .github/pending-ci-workflows.patch
    git commit -m "ci: add PR CI workflow, pin actions to SHAs"

`.github/dependabot.yml` (maven + npm + github-actions, weekly, grouped) is
already committed on this branch — it is not a workflow file, so it pushed
normally.

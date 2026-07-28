# Legacy `profiles.json` fixtures

Real save files from older releases, loaded by `LegacySaveCompatibilityTest` to prove that upgrading
does not lose a user's configuration.

| File | Written by | Contains |
|------|-----------|----------|
| `profiles-1.7.1.json` | tag `v1.7.1` (Spring Boot + JavaFX) | 3 devices (Pro / RGB / Mini), 2 profiles each, 25 command types |
| `profiles-1.8.json` | branch `releases/1.8` | the 1.7.1 file after 1.8 read and re-saved it, plus 1.8's Wave Link commands — 30 command types |
| `profiles-2.0.json` | this branch | the 1.8 file after 2.0 read and re-saved it, plus everything 2.0 added — 51 command types |

Each file is the previous one carried forward by the release that follows it, which is exactly the
path a user's file takes. They are not hand-written: each was produced by that version's own model
classes and its own Jackson setup, and verified to round-trip through it before being committed.

## Why the values look like `obsAddress-8`

Every property is filled from a counter, so no field holds its default. A test that asserted on a
default value would keep passing after that field stopped being read — the whole point is that a
dropped property shows up as a lost value. The counter also makes generation deterministic, so
regenerating produces a reviewable diff rather than noise.

`CommandNoOp` never appears: the `Commands` constructor strips it, so no save file can contain one.

## Freezing rule

**`profiles-1.7.1.json` and `profiles-1.8.json` are frozen.** They are artifacts of releases that
already shipped; regenerating them with today's code would turn the test into a tautology. Only edit
them to fix a demonstrated mistake about what those versions actually wrote.

`profiles-2.0.json` is the *current* version's file and is regenerated when the save format changes.
`LegacySaveCompatibilityTest` fails with a pointer here when a new command or `Save` property is
missing from it.

## Regenerating the current fixture

```bash
./mvnw -q test-compile -Dquarkus.native.enabled=false -Dquarkus.quinoa.enabled=false
./mvnw -q dependency:build-classpath -Dmdep.outputFile=cp-test.txt -Dmdep.includeScope=test
java -cp "target/classes:target/test-classes:$(cat cp-test.txt)" \
     com.getpcpanel.profile.compat.SaveFixtureGenerator \
     src/test/resources/legacy-saves/profiles-1.8.json \
     src/test/resources/legacy-saves/profiles-2.0.json
```

(Windows: use `;` as the classpath separator.) Review the diff before committing — it should show
only the properties and commands you added.

## Starting a new version's fixture

When a release line moves on (say 2.1), add `profiles-2.1.json` generated from `profiles-2.0.json`
with the same tool, freeze `profiles-2.0.json`, and point `CURRENT` in
`LegacySaveCompatibilityTest` at the new file. The chain is what gives each release an authentic
predecessor to load.

## How the 1.7.1 and 1.8 fixtures were made

Both predate this test, so they were generated against checkouts of those releases with a throwaway
copy of `SavePopulator` plus a small driver, using each version's own Jackson configuration (Spring
Boot's auto-configured mapper, which needs `ParameterNamesModule` for `Commands`' implicit creator).
Each driver read its own output back before writing it out, so the file is one that version genuinely
accepts. The 1.8 driver additionally asserted that no command 1.7.1 had written was lost in the
conversion.

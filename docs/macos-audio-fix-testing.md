# macOS audio fixes: independent review and controlled testing

Branch: `fix-macos-safari-pwa-volume`. Baseline: `35f20c4a`.

## Verified defect reproduction

Before changing production code, three new regressions failed on the baseline:

- Two foreground reads with different mocked `lsappinfo front` results returned the first PID.
- After a failed helper mute, resolving the same application's audio processes did not retry it.
- A different application reusing a muted application's PID inherited mute on its own helper.

## Implementation

The foreground and application-list caches are removed. Each command reads current Launch Services
data; volume and mute share focused-application resolution, and named operations reuse their application
list. Removing the list cache also prevents retaining old coalition membership across commands.

`SndCtrlOsx` resolves applications and audio-producing coalition PIDs. The existing tap service owns
application mute intent and actual tap state. There is no bundle/path/PID string mute map and no
per-helper applied flag. Application intent is keyed by the main process's PID and start instant;
live taps use the same identity model for their individual processes.

Mute intent is pending until at least one tap successfully mutes. A completely failed toggle can be
retried as mute. Once applied, application mute survives helper replacement while its main process
lives. Resolution and volume commands retry missing helper taps. Volume tap creation receives the
desired mute state directly, so recovery cannot create an unmuted volume tap for a muted application.
Unmute clears the application intent; actual mute queries read live taps. Cleanup runs on commands,
including mute queries, and closes taps from expired identities. No background monitor was added.

The automated tests cover rapid focus switching and focused termination (with process launch mocked),
mixed-state toggles, failed initial mute recovery, pending mute recovery through volume, output-device
recreation failure, same-app restart, unrelated PID reuse, helper replacement, unavailable identity,
and identity changes during tap creation. Existing Safari/PWA matching and isolation tests remain.

Fresh `lsappinfo front` + `info` + `list` took a median of 12.9 ms and a maximum of 19.0 ms over 20
read-only samples on the implementation machine (Apple Silicon, macOS 27.0.1). This is a local
measurement; assess continuous dial responsiveness on the testing machine too.

## Automated checks

Use JDK 25 and the Maven wrapper. On the implementation machine:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
MOCKITO_JAR="$HOME/.m2/repository/org/mockito/mockito-core/5.21.0/mockito-core-5.21.0.jar"
./mvnw -B -Dquarkus.native.enabled=false -Dquarkus.quinoa.enabled=false \
  "-DargLine=-javaagent:$MOCKITO_JAR" \
  -Dtest=OsxProcessHelperTest,SndCtrlOsxTest,ProcessIdentityTest,OsxPlatformCommandTest test
./mvnw -B -Dquarkus.native.enabled=false -Dquarkus.quinoa.enabled=false \
  -Dpcpanel.root=/private/tmp/pcpanel-audio-tests "-DargLine=-javaagent:$MOCKITO_JAR" test
```

The explicit Mockito agent avoids JDK 25 self-attachment failures. The full suite needs local sockets
for its mock servers. The tests above do not opt into `pcpanel.mactest`; that opt-in includes tests
which open/quit Calculator and switch the default audio output to BlackHole.

Validation used Homebrew OpenJDK 25.0.4.1 and Maven 3.9.12: all 38 focused tests passed; the full
Maven test suite reported 1,781 tests, zero failures/errors, and 22 skips. Native integration tests
and opt-in macOS audio tests were not run. The installed PCPanel application and configuration were
not modified, and no test build was launched.

One repeat full run exposed an intermittent failure in the unchanged
`AlertLightingTest.aLightShowInterruptsTheDrawingAndItResumesAfterwards` (the notification frame was
checked immediately after the asynchronous light show ended). Its isolated 20-test class recheck
passed. This is separate from the audio regressions; no lighting code was changed.

## Hands-on testing after independent review

Use macOS 14.4+ and a separate test launch. Leave the installed app bundle and its configuration alone.
Before connecting the test launch to the panel, exit any running PCPanel instance through your normal
workflow to avoid competing for the device. Build a JVM staging artifact with the configuration UI:

```sh
./mvnw -B -Dquarkus.native.enabled=false -DskipTests package
mkdir -p target/mac-audio-manual/root
"$JAVA_HOME/bin/java" -Dpcpanel.root="$PWD/target/mac-audio-manual/root" \
  -Dquarkus.http.port=17654 -jar target/quarkus-app/quarkus-run.jar skipfilecheck
```

Configure only disposable mappings in this empty test root. The JVM launch may require its own audio
recording permission. Native bundle signing/TCC identity must also be tested separately on a staged
build before release. No build or test launch should replace `/Applications/PCPanel.app`.

| Scenario | Procedure | Expected result |
| --- | --- | --- |
| Safari volume | Play audio in Safari; adjust named and focused volume; return to full volume. | Audible attenuation; full volume releases an unmuted tap. |
| Multiple PWAs | Play distinct audio in Safari and two PWAs, including two with identical display names. Use focused controls and explicit bundle-ID mappings. | Each focused/bundle-ID command changes only that application's coalition. A name mapping intentionally matches both same-named PWAs. |
| Rapid switching | Alternate focus between the audio apps and immediately issue volume and mute commands repeatedly. | Every command acts on the app focused at resolution; no 200 ms stale target window. |
| Failed mute | Attempt mute when no audio process is available, then start playback and issue a volume/mute command. Also test denied audio-recording permission and retry when the test launch has permission. | Pending mute is retried; a completely failed toggle's next attempt mutes. A volume recovery stays muted. If macOS requires restarting for a TCC change, use the no-audio-process case to test recovery within one process lifetime. |
| Helper replacement | Mute a PWA, reload/restart its WebKit audio helper while keeping the PWA's main process alive, then issue its volume command. | The replacement helper is muted on the next command and other PWAs remain unchanged. |
| Application restart | Mute, fully quit, and relaunch Safari/PWA; issue a volume command. | The new application lifetime starts without the previous lifetime's mute intent. PID reuse itself is deterministically covered by unit tests. |
| Output change | Mute and attenuate an app, switch the default output device, then issue volume/mute commands. | Taps recreate on the new output with the requested mute state; subsequent commands retry a failed recreation. |
| Focused termination | On disposable apps only, switch focus and issue focused End Program. | Only the freshly resolved foreground PID is targeted. |

Record the macOS version, launch type, permission status, main/helper PIDs, commands, and observed
audio for any failure. Finish by stopping the test process and checking that normal audio resumes.

## Limits of automated verification

CoreAudio creation and audio samples are mocked in the new regressions. Audible Safari/PWA control,
real WebKit coalition membership, TCC recovery, continuous dial responsiveness, native packaging,
and default-device transitions require hands-on testing. Process start-time information is required;
if macOS cannot provide it, per-process control skips that process rather than retaining PID-only state.
Helper replacement is reconciled on the next command, so there can be an audible interval before
that command. Foreground reads are fresh but are not atomic with application switching or termination.

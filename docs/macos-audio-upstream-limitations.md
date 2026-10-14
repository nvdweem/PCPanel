# macOS process-tap limitations for a separate upstream contribution

Compared against upstream `main` at `13afbf51` and the branch baseline `bd70328b`.
These behaviors are intentionally not fixed by the application-owner lifecycle change.

## Default output changes do not migrate active taps

`ProcessTap.aggregateDescription()` pins its aggregate subdevice and master to the output UID passed
at creation. `SndCtrlOsx` listens for the default-output property, but its callback only refreshes the
device model. Tap output reconciliation occurs on another volume/mute command.

An isolated diagnostic invoked that actual callback with mocked CoreAudio: the existing tap remained
on output 1. A volume command changing 30% to 31% closed it and created output 2. The aggregate,
listener, and lazy-recreation behavior already exist upstream. Returning to 100% is unnecessary.

A separate minimal correction should reuse the default-output notification to schedule reconciliation
of active taps, preserving each tap's requested gain/mute. Avoid native teardown on the property
callback thread, redundant recreation, and feedback from the aggregate's own device notifications.
Check that recreation failures do not discard the requested state. Verify with two real outputs and
untouched sliders, including muted taps and output disconnection.

## Gain is not restored when the actual audio process is replaced

Gain resides in live `ProcessTap` objects. When the PID/start identity is replaced, its old tap must
be released. A subsequent mute creates a new tap at gain 1 if no live gain exists. The macOS backend
does not publish the session events that drive `NewSessionVolumeService`'s automatic dial replay.

Isolated diagnostics confirmed a genuinely new audio process loses the old 30% gain after both muted
and unmuted restarts. Observed Firefox retention when unmuted requires checking whether its tapped
audio process survives or another command reapplies gain. The lifecycle fix preserves attenuation on
the *same surviving audio process* while removing the departed owner's mute. It adds no cross-process
gain persistence and no automatic volume replay.

Automatic restoration for a genuinely new process would require stable application targeting plus
notification-driven detection of new audio processes, or integration with existing session/dial
replay. PWA targets must use their unique bundle identities rather than a shared executable/display
name. Decide and test this separately; merely caching gains does not implement automatic restoration.

## Mute tap startup can pass audio before the mute flag is set

`ProcessTap.create()` constructs a tap with Java's default `muted=false` and calls `startRendering()`.
Only after creation returns does the tap service call `setMuted(true)`. The render callback can run
between those operations. This ordering is unchanged from upstream and remains reachable when our
application mute creates its initial or replacement-helper taps.

A diagnostic instantiated the actual `ProcessTap` object and invoked its actual render routine with
synthetic JNA audio buffers. An input sample of 1 at gain 0.5 produced 0.5 before `setMuted(true)` and
0 afterward. It created no native tap, aggregate, or live audio stream. This demonstrates the unsafe
startup state, not the duration or audibility of the window on macOS.

The safest minimal separate correction is to pass an initial mute flag into creation and initialize
it before `startRendering()`. Keep the requested gain unchanged so unmute restores it. Existing
three-argument creation callers can delegate with `initialMuted=false`. Test initial callback samples
and actual loopback audio; avoid treating a later mute write as proof that the first callback was
silent. This commit changes `ProcessTap` only to add creation/cleanup diagnostic logs.

## Native resource cleanup remains a hands-on check

`ProcessTap.close()` requests IO stop, IO-proc destruction, aggregate destruction, and tap destruction.
Existing native errors are logged at DEBUG; the method marks itself closed before these calls and
does not retry failed destruction. The controlled-test logs available during investigation contained
only startup/shutdown information, so they do not establish that native destruction failed.

Do not add a destruction retry mechanism without evidence. Use the focused restart experiment in
[the testing guide](macos-audio-fix-testing.md) to correlate owner/helper identities, native tap and
aggregate IDs, and destruction statuses. Persistent silence after successful owner release is a
different result from a helper legitimately surviving with an old Java mute flag.

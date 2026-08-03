#pragma once
#include "pch.h"

DWORD   GetFocusProcessId();
wstring GetProcessName(DWORD procId);

// Single global lock serialising access to the audio-state maps (SndCtrl::devices and every
// AudioDevice::sessions). They are mutated from the COM apartment / notification thread and read or
// mutated from the JNI worker threads that service Java volume/mute calls, so without this they
// race. One coarse lock (rather than one per map) keeps it free of lock-ordering deadlocks; audio
// control operations are infrequent so contention is irrelevant. Recursive because an STA COM call
// can pump messages and re-enter a notification on the same thread that already holds the lock.
//
// Timed, because everything Java calls in here runs on the one thread that executes every dial and
// button. Nothing may hold this lock indefinitely -- the callbacks now do only map bookkeeping under
// it -- but if anything ever does again, a call that waits forever takes all hardware control with
// it. So the calls Java makes wait a bounded time and give up instead.
extern std::recursive_timed_mutex g_audioMutex;

// Longer than CommandDispatcher.STUCK_COMMAND_THRESHOLD_MS (5s) on purpose: the watchdog gets to name
// the blocked command first, then the call gives up and the watchdog reports it running again. A stall
// that used to be permanent and silent becomes a slow dial with both ends of it in the log.
constexpr auto AUDIO_LOCK_TIMEOUT = std::chrono::seconds(8);
using AudioLock = std::unique_lock<std::recursive_timed_mutex>;

// Acquires g_audioMutex for a call made from Java, or gives up. Check owns_lock() before touching the
// maps; the caller must do nothing rather than proceed unlocked.
inline AudioLock lockAudioForJavaCall() {
    return AudioLock(g_audioMutex, AUDIO_LOCK_TIMEOUT);
}

#undef DEBUG

#define NULLRETURN(x) if (!x) return;
#define NULLRETURNVAL(x, v) if (!(x)) return v;
#define NULLCONTINUE(x) if (!x) continue;

template<class T> T* notNull(T* t, int line, const char* file) {
    cout << t << ": " << file << " (" << line << ")" << endl;
    return t;
}

template<class T> T& notNull(T& t, int line, const char* file) {
    cout << t << ": " << file << " (" << line << ")" << endl;
    return t;
}

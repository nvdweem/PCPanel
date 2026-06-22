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
extern std::recursive_mutex g_audioMutex;

#undef DEBUG

#define NULLRETURN(x) if (!x) return;
#define NULLCONTINUE(x) if (!x) continue;

template<class T> T* notNull(T* t, int line, const char* file) {
    cout << t << ": " << file << " (" << line << ")" << endl;
    return t;
}

template<class T> T& notNull(T& t, int line, const char* file) {
    cout << t << ": " << file << " (" << line << ")" << endl;
    return t;
}

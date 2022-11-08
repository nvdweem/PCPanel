#pragma once
#include "pch.h"

DWORD   GetFocusProcessId();
wstring GetProcessName(DWORD procId);

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

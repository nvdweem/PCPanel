#pragma once
#include "pch.h"

DWORD   GetFocusProcessId();
wstring GetProcessName(DWORD procId);

#ifdef DEBUG
#define NOTNULL(x) notNull(x, __LINE__, __FILE__)
#else
#define NOTNULL(x) x
#endif // DEBUG

template<class T> T* notNull(T* t, int line, const char* file) {
    cout << t << ": " << file << " (" << line << ")" << endl;
    return t;
}

template<class T> T& notNull(T& t, int line, const char* file) {
    cout << t << ": " << file << " (" << line << ")" << endl;
    return t;
}

#include "pch.h"
#include "helpers.h"

DWORD GetFocusProcessId()
{
    DWORD procId;
    GetWindowThreadProcessId(GetForegroundWindow(), &procId);
    return (int)procId;
}

wstring GetProcessName(DWORD procId) {
    DWORD buffSize = MAX_PATH;
    WCHAR buffer[MAX_PATH] = { 0 };
    HANDLE hProc = OpenProcess(PROCESS_QUERY_INFORMATION, FALSE, procId);
    if (!hProc) {
        return wstring();
    }
    QueryFullProcessImageName(hProc, NULL, buffer, &buffSize);
    CloseHandle(hProc);
    return wstring(buffer);
}

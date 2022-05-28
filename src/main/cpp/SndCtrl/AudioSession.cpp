#include "pch.h"
#include "AudioSession.h"
#include <winver.h>


CComPtr<IAudioSessionControl2> GetSession2(IAudioSessionControl& control)
{
    CComPtr<IAudioSessionControl2> pSessionControl2 = NULL;
    control.QueryInterface(__uuidof(IAudioSessionControl2), (void**)&pSessionControl2);
    return pSessionControl2;
}

DWORD GetProcessId(IAudioSessionControl2& control2) {
    DWORD procID;
    control2.GetProcessId(&procID);
    return procID;
}

wstring ProcessIdToName(DWORD processId) {
    HANDLE handle = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, processId);
    if (handle) {
        WCHAR buffer[1024];
        DWORD bufferSize = 1024;
        if (!QueryFullProcessImageName(handle, 0, buffer, &bufferSize)) {
            printf("Error GetModuleBaseNameA : %lu", GetLastError());
        }
        CloseHandle(handle);
        return wstring(buffer);
    }
    else {
        printf("Error OpenProcess : %lu", GetLastError());
        return wstring();
    }
}

AudioSession::AudioSession(CComPtr<IAudioSessionControl> session)
    : pSession(session), pListener(nullptr), pid(0), name()
{
    auto session2 = GetSession2(*session);
    pid = GetProcessId(*session2);
    if (pid > 0) {
        name = ProcessIdToName(pid);
    }
}

typedef std::basic_string<TCHAR> stlString;

void AudioSession::Init(JniCaller& audioDevice, function<void()> onRemoved)
{
    LPWSTR icon = NULL;
    pSession->GetIconPath(&icon);
    co_ptr<WCHAR> pIcon(icon);

    BOOL muted;
    CComQIPtr<ISimpleAudioVolume> cc = pSession.p;
    float level = 0;
    cc->GetMasterVolume(&level);
    cc->GetMute(&muted);

    auto pname = GetProductName();
    JThread thread;
    auto jObj = audioDevice.CallObject("addSession", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;FZ)Lcom/getpcpanel/cpp/AudioSession;",
        pid, thread.jstr(name.c_str()), thread.jstr(pname.c_str()), thread.jstr(icon), level, muted
    );

    pListener = make_unique<AudioSessionListener>(pSession, [&]() {
        JThread thread;
        audioDevice.CallVoid("removeSession", "(I)V", pid);       
        onRemoved();
    }, jObj);
}

basic_string<TCHAR> AudioSession::GetProductName()
{
    DWORD	dwHandle;
    DWORD	dwFileVersionInfoSize = GetFileVersionInfoSize(name.c_str(), &dwHandle);
    LPVOID	lpData = (LPVOID)new BYTE[dwFileVersionInfoSize];
    LPVOID	lpInfo;
    UINT	unInfoLen;
    if (GetFileVersionInfo(name.c_str(), dwHandle, dwFileVersionInfoSize, lpData)) {
        if (VerQueryValue(lpData, _T("\\StringFileInfo\\040904B0\\ProductName"), &lpInfo, &unInfoLen))
            return stlString((LPCTSTR)lpInfo);
    }
    return _T("");
}

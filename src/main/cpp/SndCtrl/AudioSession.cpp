#include "pch.h"
#include "AudioSession.h"
#include "helpers.h"
#include <winver.h>


CComPtr<IAudioSessionControl2> GetSession2(IAudioSessionControl& control) {
    CComPtr<IAudioSessionControl2> cpSessionControl2 = NULL;
    control.QueryInterface(__uuidof(IAudioSessionControl2), (void**)&cpSessionControl2);
    return cpSessionControl2;
}

DWORD GetProcessId(IAudioSessionControl2& control2) {
    DWORD procID;
    control2.GetProcessId(&procID);
    return procID;
}

AudioSession::AudioSession(CComPtr<IAudioSessionControl> session) :
    cpSession(session),
    cpListener(nullptr),
    cpVolumeControl(session),
    pid(0),
    name() {
    auto cpSession2 = GetSession2(*session);
    NULLRETURN(cpSession2)
    pid = GetProcessId(*cpSession2);
    if (pid > 0) {
        name = GetProcessName(pid);
    }
}

void AudioSession::Init(JniCaller& audioDevice, AudioSessionListenerCB& callback) {
    NULLRETURN(cpSession)

    LPWSTR icon = NULL;
    cpSession->GetIconPath(&icon);
    co_ptr<WCHAR> pIcon(icon);

    BOOL muted = false;
    CComQIPtr<ISimpleAudioVolume> cc(cpSession);
    float level = 0;
    if (cc) {
        cc->GetMasterVolume(&level);
        cc->GetMute(&muted);
    }

    auto pname = GetProductName();
    auto nameCopy = pname;
    JThread thread;
    if (*thread) {
        auto nameStr = thread.jstr(name.c_str());
        auto pNameStr = thread.jstr(pname.c_str());
        auto iconStr = thread.jstr(icon);
        jlong pointer = reinterpret_cast<std::uintptr_t>(this);
        auto jObj = audioDevice.CallObject(thread, "addSession", "(JILjava/lang/String;Ljava/lang/String;Ljava/lang/String;FZ)Lcom/getpcpanel/cpp/AudioSession;",
            pointer, pid, nameStr, pNameStr, iconStr, level, muted
        );
        thread.jstr(nameStr);
        thread.jstr(pNameStr);
        thread.jstr(iconStr);

        cpListener.Set(new AudioSessionListener(cpSession, *this, callback, jObj));
    }
}

void AudioSession::SetVolume(float volume) {
    NULLRETURN(cpVolumeControl)
    cpVolumeControl->SetMasterVolume(volume, nullptr);
}

void AudioSession::Mute(bool muted) {
    NULLRETURN(cpVolumeControl)
    cpVolumeControl->SetMute(muted, nullptr);
}

basic_string<TCHAR> AudioSession::GetProductName() {
    DWORD	dwHandle;
    DWORD	dwFileVersionInfoSize = GetFileVersionInfoSize(name.c_str(), &dwHandle);
    LPVOID	lpData = (LPVOID)new BYTE[dwFileVersionInfoSize];
    LPVOID	lpInfo;
    UINT	unInfoLen;
    if (GetFileVersionInfo(name.c_str(), dwHandle, dwFileVersionInfoSize, lpData)) {
        if (VerQueryValue(lpData, _T("\\StringFileInfo\\040904B0\\ProductName"), &lpInfo, &unInfoLen))
            return wstring((LPCTSTR)lpInfo);
    }
    return _T("");
}

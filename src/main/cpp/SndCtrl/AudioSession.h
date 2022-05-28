#pragma once
#include "Listeners.h"
#include "JniCaller.h"

class AudioSession
{
private:
    CComPtr<IAudioSessionControl> pSession;
    unique_ptr<AudioSessionListener> pListener;

    int pid;
    wstring name;
public:
    AudioSession(CComPtr<IAudioSessionControl> ctrl);
    AudioSession(const AudioSession&) = delete;

    void Init(JniCaller& audioDevice, function<void()> onRemoved);

    int GetPid() {
        return pid;
    }

private:
    basic_string<TCHAR> GetProductName();
};

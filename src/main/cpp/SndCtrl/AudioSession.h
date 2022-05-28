#pragma once
#include "Listeners.h"
#include "JniCaller.h"

class AudioSession
{
private:
    CComPtr<IAudioSessionControl> pSession;
    unique_ptr<AudioSessionListener> pListener;
    CComQIPtr<ISimpleAudioVolume> pVolumeControl;

    int pid;
    wstring name;
public:
    AudioSession(CComPtr<IAudioSessionControl> ctrl);
    AudioSession(const AudioSession&) = delete;

    void Init(JniCaller& audioDevice, function<void()> onRemoved);
    void SetVolume(float volume);
    void Mute(bool muted);

    int GetPid() const {
        return pid;
    }
    const wstring& GetName() const {
        return name;
    }

private:
    basic_string<TCHAR> GetProductName();
};

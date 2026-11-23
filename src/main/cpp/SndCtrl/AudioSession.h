#pragma once
#include "JniCaller.h"
#include "Listeners.h"

class AudioSession
{
private:
    CComPtr<IAudioSessionControl> cpSession;
    CComQIPtr<ISimpleAudioVolume> cpVolumeControl;

    int pid;
    wstring name;

    StoppingHandle<AudioSessionListener> cpListener;
public:
    AudioSession(CComPtr<IAudioSessionControl> ctrl);
    AudioSession(const AudioSession&) = delete;

    void Init(JniCaller& audioDevice, AudioSessionListenerCB& callback);
    // The HRESULT Windows answered: AUDCLNT_E_DEVICE_INVALIDATED means this handle no longer reaches the session.
    HRESULT SetVolume(float volume);
    HRESULT Mute(bool muted);

    int GetPid() const {
        return pid;
    }
    const wstring& GetName() const {
        return name;
    }

private:
    basic_string<TCHAR> GetProductName();
};

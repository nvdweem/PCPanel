#pragma once
#include "AudioSession.h"
#include "JniCaller.h"
#include "Listeners.h"
#include "helpers.h"


class AudioDevice : public SessionListenerCB, public AudioSessionListenerCB
{
private:
    JniCaller jni;
    CComPtr<IMMDevice> cpDevice;
    EDataFlow dataFlow;
    CComPtr<IAudioEndpointVolume> cpVolume;

    wstring id;
    unordered_map<int, list<unique_ptr<AudioSession>>> sessions;

    StoppingHandle<DeviceVolumeListener> cpDeviceVolumeListener;
    StoppingHandle<SessionListener> cpSessionListener;
public:
    AudioDevice(wstring id, CComPtr<IMMDevice> cpDevice, EDataFlow dataFlow, jobject obj);
    AudioDevice(const AudioDevice&) = delete;

    virtual void OnNewSession(IAudioSessionControl* cpSess) {
        NULLRETURN(cpSess);
        SessionAdded(cpSess);
    };

    // Called from Java
    void SetVolume(float volume);
    void Mute(bool muted);
    bool SetProcessVolume(int pid, float volume);
    bool MuteProcess(int pid, bool muted);
    void SetDefault(EDataFlow dataFlow, ERole role);

    const unordered_map<int, list<unique_ptr<AudioSession>>>& GetSessions() const {
        return sessions;
    }

    boolean IsOutput() {
        return dataFlow == eRender || dataFlow == eAll;
    }

    virtual void SessionRemoved(AudioSession& session) override;
private:
    void SessionAdded(CComPtr<IAudioSessionControl> cpSession);

    CComPtr<IAudioSessionManager2> Activate(IMMDevice& device);
    CComPtr<IAudioSessionEnumerator> GetSessionEnumerator(IAudioSessionManager2& sessionManager);
    int GetCount(IAudioSessionEnumerator& collection);
    CComPtr<IAudioSessionControl> GetSession(IAudioSessionEnumerator& collection, int idx);

    CComPtr<IAudioEndpointVolume> GetVolumeControl(IMMDevice& device) {
        CComPtr<IAudioEndpointVolume> cpVol;
        device.Activate(__uuidof(IAudioEndpointVolume), CLSCTX_ALL, NULL, (void**)&cpVol);
        return cpVol;
    }
};

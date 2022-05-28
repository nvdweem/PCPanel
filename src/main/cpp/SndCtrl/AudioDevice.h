#pragma once
#include "JniCaller.h"
#include "Listeners.h"
#include "AudioSession.h"


class AudioDevice : public SessionListenerCB
{
private:
    wstring id;
    CComPtr<IMMDevice> pDevice;

    CComPtr<IAudioEndpointVolume> pVolume;
    JniCaller jni;

    DeviceVolumeListener deviceVolumeListener;
    unique_ptr<SessionListener> pSessionListener;

    unordered_map<int, unique_ptr<AudioSession>> sessions;
public:
    AudioDevice(wstring id, CComPtr<IMMDevice> pDevice, jobject obj);
    AudioDevice(const AudioDevice&) = delete;

    virtual void OnNewSession(IAudioSessionControl* pSess) {
        SessionAdded(pSess);
    };

    // Called from Java
    void SetVolume(float volume);
    bool SetProcessVolume(int pid, float volume);

    const unordered_map<int, unique_ptr<AudioSession>>& GetSessions() const {
        return sessions;
    }
private:
    void SessionAdded(CComPtr<IAudioSessionControl> session);

    CComPtr<IAudioSessionManager2> Activate(IMMDevice& device);
    CComPtr<IAudioSessionEnumerator> GetSessionEnumerator(IAudioSessionManager2& sessionManager);
    int GetCount(IAudioSessionEnumerator& collection);
    CComPtr<IAudioSessionControl> GetSession(IAudioSessionEnumerator& collection, int idx);


    CComPtr<IAudioEndpointVolume> GetVolumeControl(IMMDevice& device) {
        CComPtr<IAudioEndpointVolume> pVol;
        device.Activate(__uuidof(IAudioEndpointVolume), CLSCTX_ALL, NULL, (void**)&pVol);
        return pVol;
    }
};


#pragma once
#include "JniCaller.h"
#include "Listeners.h"
#include "AudioSession.h"


class AudioDevice : public SessionListenerCB
{
private:
    wstring id;
    CComPtr<IMMDevice> pDevice;
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

private:
    void SessionAdded(CComPtr<IAudioSessionControl> session);

    CComPtr<IAudioSessionManager2> Activate(IMMDevice& device);
    CComPtr<IAudioSessionEnumerator> GetSessionEnumerator(IAudioSessionManager2& sessionManager);
    int GetCount(IAudioSessionEnumerator& collection);
    CComPtr<IAudioSessionControl> GetSession(IAudioSessionEnumerator& collection, int idx);

};


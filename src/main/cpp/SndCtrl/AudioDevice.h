#pragma once
#include "AudioSession.h"
#include "JniCaller.h"
#include "Listeners.h"
#include "helpers.h"


// What became of a call on an app's sessions: no session of that pid, applied, or at least one handle that no longer
// reaches its session (AUDCLNT_E_DEVICE_INVALIDATED) and needs the sessions re-read.
enum class SessionCall { NotFound, Applied, Invalidated };

// Re-reads the sessions of the endpoint with this id once Windows has had time to settle it; repeated requests while
// one is waiting add nothing. Safe to call from a notification callback: it only starts a thread.
void RequestSessionRebuild(const wstring& deviceId);

class AudioDevice : public SessionListenerCB, public AudioSessionListenerCB
{
private:
    shared_ptr<JniCaller> jni;
    CComPtr<IMMDevice> cpDevice;
    EDataFlow dataFlow;
    CComPtr<IAudioEndpointVolume> cpVolume;

    wstring id;
    unordered_map<int, list<unique_ptr<AudioSession>>> sessions;
    // One rebuild of the sessions at a time; a second waits and then reads them again.
    std::mutex rebuildMutex;

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
    SessionCall SetProcessVolume(int pid, float volume);
    SessionCall MuteProcess(int pid, bool muted);
    void SetDefault(EDataFlow dataFlow, ERole role);

    /**
     * Replaces every session handle with a freshly read one. Windows invalidates an endpoint's session handles when it
     * renegotiates the endpoint's format, which a Bluetooth device does right after it reconnects; the handles taken
     * before then fail with AUDCLNT_E_DEVICE_INVALIDATED for good, while the sessions themselves live on. Takes the audio
     * lock only for the map, so it must be called without holding it.
     */
    void RebuildSessions();

    const wstring& GetId() const {
        return id;
    }

    const unordered_map<int, list<unique_ptr<AudioSession>>>& GetSessions() const {
        return sessions;
    }

    boolean IsOutput() {
        return dataFlow == eRender || dataFlow == eAll;
    }

    virtual void SessionRemoved(AudioSession& session) override;
    virtual void SessionDisconnected(AudioSessionDisconnectReason reason) override;
private:
    void SessionAdded(CComPtr<IAudioSessionControl> cpSession);
    // Listens for new sessions on the manager and adds the ones already there.
    void AttachSessions(CComPtr<IAudioSessionManager2> cpSessionManager);
    template<typename Call> SessionCall ForEachSessionOf(int pid, Call call);

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

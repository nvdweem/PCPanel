#include "pch.h"
#include "AudioDevice.h"
#include "policyconfig.h"

AudioDevice::AudioDevice(wstring id, CComPtr<IMMDevice> cpDevice, EDataFlow dataFlow, jobject obj) :
    id(id),
    cpDevice(cpDevice),
    dataFlow(dataFlow),
    jni(JniCaller::CreateShared(obj)),
    cpVolume(GetVolumeControl(*cpDevice)),
    cpDeviceVolumeListener(new DeviceVolumeListener(cpVolume, jni)),
    cpSessionListener() {

    auto cpSessionManager = Activate(*cpDevice);
    NULLRETURN(cpSessionManager)
    AttachSessions(cpSessionManager);
}

void AudioDevice::AttachSessions(CComPtr<IAudioSessionManager2> cpSessionManager) {
    cpSessionListener.Set(new SessionListener(*this, cpSessionManager));

    auto cpSessionList = GetSessionEnumerator(*cpSessionManager);
    if (cpSessionList) {
        auto sessionCount = GetCount(*cpSessionList);
        for (int index = 0; index < sessionCount; index++) {
            auto session = GetSession(*cpSessionList, index);
            NULLCONTINUE(session)
            SessionAdded(session);
        }
    }
}

void AudioDevice::RebuildSessions() {
    std::lock_guard<std::mutex> rebuilding(rebuildMutex);
    auto cpSessionManager = Activate(*cpDevice);
    NULLRETURN(cpSessionManager)

    unordered_map<int, list<unique_ptr<AudioSession>>> stale;
    {
        std::lock_guard<std::recursive_timed_mutex> lock(g_audioMutex);
        stale.swap(sessions);
    }

    // The fresh handles reach Java before the stale ones leave it: Java keeps an app's session for as long as one of
    // its handles is registered, so the session stays in place rather than disappearing and coming back.
    AttachSessions(cpSessionManager);
    {
        JThread thread;
        for (auto& entry : stale) {
            for (auto& session : entry.second) {
                jlong pointer = reinterpret_cast<std::uintptr_t>(session.get());
                jni->CallVoid(thread, "removeSession", "(JI)V", pointer, entry.first);
            }
        }
    }

    // Released on a thread of its own, as SessionRemoved does: releasing a handle unregisters its event sink, and a
    // notification for it may be running at this moment.
    if (!stale.empty()) {
        std::thread([s = std::move(stale)]() mutable { s.clear(); }).detach();
    }
}

void AudioDevice::SessionDisconnected(AudioSessionDisconnectReason reason) {
    // An endpoint that went away is handled where it is removed, which also releases this device.
    if (reason == DisconnectReasonDeviceRemoval) {
        return;
    }
    RequestSessionRebuild(id);
}

void AudioDevice::SetVolume(float volume) {
    NULLRETURN(cpVolume)
    cpVolume->SetMasterVolumeLevelScalar(volume, nullptr);
}

void AudioDevice::Mute(bool muted) {
    NULLRETURN(cpVolume)
    cpVolume->SetMute(muted, nullptr);
}

template<typename Call> SessionCall AudioDevice::ForEachSessionOf(int pid, Call call) {
    std::lock_guard<std::recursive_timed_mutex> lock(g_audioMutex);
    auto entry = sessions.find(pid);
    if (entry == sessions.end()) {
        return SessionCall::NotFound;
    }
    auto result = SessionCall::Applied;
    for (auto& sess : entry->second) {
        if (call(*sess) == AUDCLNT_E_DEVICE_INVALIDATED) {
            result = SessionCall::Invalidated;
        }
    }
    return result;
}

SessionCall AudioDevice::SetProcessVolume(int pid, float volume) {
    return ForEachSessionOf(pid, [volume](AudioSession& sess) { return sess.SetVolume(volume); });
}

SessionCall AudioDevice::MuteProcess(int pid, bool muted) {
    return ForEachSessionOf(pid, [muted](AudioSession& sess) { return sess.Mute(muted); });
}

void AudioDevice::SetDefault(EDataFlow dataFlow, ERole role) {
    CComPtr<IPolicyConfigVista> cpPolicyConfig;
    ERole reserved = (ERole)role;

    HRESULT hr = CoCreateInstance(__uuidof(CPolicyConfigVistaClient), NULL, CLSCTX_ALL, __uuidof(IPolicyConfigVista), (LPVOID*)&cpPolicyConfig);
    if (SUCCEEDED(hr)) {
        NULLRETURN(cpPolicyConfig);
        cpPolicyConfig->SetDefaultEndpoint(id.c_str(), reserved);
    }
}

void AudioDevice::SessionRemoved(AudioSession& session) {
    auto pid = session.GetPid();
    std::unique_ptr<AudioSession> removed;
    {
        std::lock_guard<std::recursive_timed_mutex> lock(g_audioMutex);

        auto entry = sessions.find(pid);
        if (entry != sessions.end()) {
            auto& list = entry->second;
            for (auto it = list.begin(); it != list.end(); ++it) {
                if (it->get() == &session) {
                    removed = std::move(*it); // take ownership out of the map; destroy it below
                    list.erase(it);
                    break;
                }
            }
            if (list.empty()) {
                sessions.erase(pid);
            }
        }
    }

    // Told outside the mutex, because the callback runs application code and this mutex is the one
    // every volume call has to take. The session stays alive across the call: `removed` still owns it,
    // and its address serves only as an identifier on the Java side.
    {
        JThread thread;
        jlong pointer = reinterpret_cast<std::uintptr_t>(&session);
        jni->CallVoid(thread, "removeSession", "(JI)V", pointer, pid);
    }

    // Destroy the session (which calls UnregisterAudioSessionNotification) off this COM event
    // callback: unregistering an IAudioSessionEvents sink from inside that same sink's OnStateChanged
    // can deadlock WASAPI. The detached thread owns only the extracted session, so it has no
    // dependency on this AudioDevice's lifetime.
    if (removed) {
        std::thread([r = std::move(removed)]() mutable { r.reset(); }).detach();
    }
}

void AudioDevice::SessionAdded(CComPtr<IAudioSessionControl> session) {
    // Init reads COM properties, registers the session sink and calls up into Java, which runs
    // application code -- so it happens before the mutex is taken. That mutex is the one every volume
    // call needs, and this is a notification callback that may not block.
    //
    // Init before insert, not after: the session identifies itself to Java by its own address, which
    // moving the owning pointer into the map does not change. The other order would mean calling Init
    // through a raw pointer with the mutex released, which a concurrent removal could free underneath.
    auto ptr = make_unique<AudioSession>(session);
    auto pid = ptr->GetPid();
    ptr->Init(*jni, *this);

    std::lock_guard<std::recursive_timed_mutex> lock(g_audioMutex);
    sessions[pid].push_back(std::move(ptr));
}

CComPtr<IAudioSessionManager2> AudioDevice::Activate(IMMDevice& device) {
    CComPtr<IAudioSessionManager2> cpSessionManager;
    device.Activate(__uuidof(IAudioSessionManager2), CLSCTX_ALL, NULL, (void**)&cpSessionManager);
    return cpSessionManager;
}

CComPtr<IAudioSessionEnumerator> AudioDevice::GetSessionEnumerator(IAudioSessionManager2& sessionManager) {
    CComPtr<IAudioSessionEnumerator> cpSessionList;
    sessionManager.GetSessionEnumerator(&cpSessionList);
    return cpSessionList;
}

int AudioDevice::GetCount(IAudioSessionEnumerator& collection) {
    int sessionCount = 0;
    if (FAILED(collection.GetCount(&sessionCount))) {
        return 0;
    }
    return sessionCount;
}

CComPtr<IAudioSessionControl> AudioDevice::GetSession(IAudioSessionEnumerator& collection, int idx) {
    CComPtr<IAudioSessionControl> cpSessionControl;
    collection.GetSession(idx, &cpSessionControl);
    return cpSessionControl;
}

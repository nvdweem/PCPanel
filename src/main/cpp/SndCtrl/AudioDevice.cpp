#include "pch.h"
#include "AudioDevice.h"
#include "policyconfig.h"

AudioDevice::AudioDevice(wstring id, CComPtr<IMMDevice> cpDevice, EDataFlow dataFlow, jobject obj) :
    id(id),
    cpDevice(cpDevice),
    dataFlow(dataFlow),
    jni(JniCaller::Create(obj)),
    cpVolume(GetVolumeControl(*cpDevice)),
    cpDeviceVolumeListener(new DeviceVolumeListener(cpVolume, jni)),
    cpSessionListener() {

    auto cpSessionManager = Activate(*cpDevice);
    NULLRETURN(cpSessionManager)

    cpSessionListener.Set(new SessionListener(*this, cpSessionManager));

    // Get current sessions
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

void AudioDevice::SetVolume(float volume) {
    NULLRETURN(cpVolume)
    cpVolume->SetMasterVolumeLevelScalar(volume, nullptr);
}

void AudioDevice::Mute(bool muted) {
    NULLRETURN(cpVolume)
    cpVolume->SetMute(muted, nullptr);
}

bool AudioDevice::SetProcessVolume(int pid, float volume) {
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
    auto entry = sessions.find(pid);
    if (entry != sessions.end()) {
        for (auto& sess : entry->second) {
            sess->SetVolume(volume);
        }
        return true;
    }
    return false;
}

bool AudioDevice::MuteProcess(int pid, bool muted) {
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
    auto entry = sessions.find(pid);
    if (entry != sessions.end()) {
        for (auto& sess : entry->second) {
            sess->Mute(muted);
        }
        return true;
    }
    return false;
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
        std::lock_guard<std::recursive_mutex> lock(g_audioMutex);

        JThread thread;
        jlong pointer = reinterpret_cast<std::uintptr_t>(&session);
        jni.CallVoid(thread, "removeSession", "(JI)V", pointer, pid);

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

    // Destroy the session (which calls UnregisterAudioSessionNotification) off this COM event
    // callback: unregistering an IAudioSessionEvents sink from inside that same sink's OnStateChanged
    // can deadlock WASAPI. The detached thread owns only the extracted session, so it has no
    // dependency on this AudioDevice's lifetime.
    if (removed) {
        std::thread([r = std::move(removed)]() mutable { r.reset(); }).detach();
    }
}

void AudioDevice::SessionAdded(CComPtr<IAudioSessionControl> session) {
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
    auto ptr = make_unique<AudioSession>(session);
    auto pid = ptr->GetPid();
    auto raw = ptr.get();
    auto& target = sessions[pid];
    target.push_back(std::move(ptr));
    raw->Init(jni, *this);
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
    int sessionCount;
    collection.GetCount(&sessionCount);
    return sessionCount;
}

CComPtr<IAudioSessionControl> AudioDevice::GetSession(IAudioSessionEnumerator& collection, int idx) {
    CComPtr<IAudioSessionControl> cpSessionControl;
    collection.GetSession(idx, &cpSessionControl);
    return cpSessionControl;
}

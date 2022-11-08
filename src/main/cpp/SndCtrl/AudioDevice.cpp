#include "pch.h"
#include "AudioDevice.h"
#include "policyconfig.h"

AudioDevice::AudioDevice(wstring id, CComPtr<IMMDevice> cpDevice, jobject obj) :
    id(id),
    cpDevice(cpDevice),
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
    JThread thread;
    auto pid = session.GetPid();
    jlong pointer = reinterpret_cast<std::uintptr_t>(&session);
    jni.CallVoid(thread, "removeSession", "(JI)V", pointer, pid);

    auto entry = sessions.find(pid);
    if (entry == sessions.end()) {
        auto& list = entry->second;
        list.remove_if([&session](auto& pU) {return pU.get() == &session; });
        if (list.empty()) {
            sessions.erase(pid);
            cout << "Clear session " << pid << endl;
        }
    }
}

void AudioDevice::SessionAdded(CComPtr<IAudioSessionControl> session) {
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

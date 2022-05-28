#include "pch.h"
#include "AudioDevice.h"
#include "policyconfig.h"

AudioDevice::AudioDevice(wstring id, CComPtr<IMMDevice> pDevice, jobject obj)
    : id(id),
      pDevice(pDevice),
      jni(JniCaller::Create(obj)),
      pVolume(GetVolumeControl(*pDevice)),
      deviceVolumeListener(pVolume, jni)
{
    auto pSessionManager = Activate(*pDevice);
    pSessionListener = make_unique<SessionListener>(*this, pSessionManager);
    
    // Get current sessions
    auto pSessionList = GetSessionEnumerator(*pSessionManager);
    auto sessionCount = GetCount(*pSessionList);
    for (int index = 0; index < sessionCount; index++) {
        auto session = GetSession(*pSessionList, index);
        SessionAdded(session);
    }
}

void AudioDevice::SetVolume(float volume)
{
    pVolume->SetMasterVolumeLevelScalar(volume, nullptr);
}

void AudioDevice::Mute(bool muted)
{
    pVolume->SetMute(muted, nullptr);
}

bool AudioDevice::SetProcessVolume(int pid, float volume)
{
    auto entry = sessions.find(pid);
    if (entry != sessions.end()) {
        entry->second->SetVolume(volume);
        return true;
    }
    return false;
}

bool AudioDevice::MuteProcess(int pid, bool muted)
{
    auto entry = sessions.find(pid);
    if (entry != sessions.end()) {
        entry->second->Mute(muted);
        return true;
    }
    return false;
}

void AudioDevice::SetDefault(EDataFlow dataFlow, ERole role)
{
    CComPtr<IPolicyConfigVista> pPolicyConfig;
    ERole reserved = (ERole)role;

    HRESULT hr = CoCreateInstance(__uuidof(CPolicyConfigVistaClient), NULL, CLSCTX_ALL, __uuidof(IPolicyConfigVista), (LPVOID*)&pPolicyConfig);
    if (SUCCEEDED(hr)) {
        pPolicyConfig->SetDefaultEndpoint(id.c_str(), reserved);
    }
}

void AudioDevice::SessionAdded(CComPtr<IAudioSessionControl> session)
{
    auto ptr = make_unique<AudioSession>(session);
    auto pid = ptr->GetPid();
    ptr->Init(jni, [this, pid]() { sessions.erase(pid); });
    sessions.insert({pid, std::move(ptr)});
}

CComPtr<IAudioSessionManager2> AudioDevice::Activate(IMMDevice& device)
{
    CComPtr<IAudioSessionManager2> pSessionManager;
    device.Activate(__uuidof(IAudioSessionManager2), CLSCTX_ALL, NULL, (void**)&pSessionManager);
    return pSessionManager;
}

CComPtr<IAudioSessionEnumerator> AudioDevice::GetSessionEnumerator(IAudioSessionManager2& sessionManager)
{
    CComPtr<IAudioSessionEnumerator> pSessionList;
    sessionManager.GetSessionEnumerator(&pSessionList);
    return pSessionList;
}

int AudioDevice::GetCount(IAudioSessionEnumerator& collection)
{
    int sessionCount;
    collection.GetCount(&sessionCount);
    return sessionCount;
}

CComPtr<IAudioSessionControl> AudioDevice::GetSession(IAudioSessionEnumerator& collection, int idx)
{
    CComPtr<IAudioSessionControl> pSessionControl;
    collection.GetSession(idx, &pSessionControl);
    return pSessionControl;
}

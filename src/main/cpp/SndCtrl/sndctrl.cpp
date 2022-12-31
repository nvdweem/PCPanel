#include "pch.h"
#include "sndctrl.h"
#include "JniCaller.h"
#include "helpers.h"

unique_ptr<SndCtrl> pSndCtrl;

SndCtrl::SndCtrl(JNIEnv* env, jobject obj) :
    pJni(new JniCaller(env, obj)),
    cpDeviceListener(nullptr) {

    if (CoInitialize(nullptr) != S_OK) {
        cerr << "Unable to CoInitialize" << endl;
    }

    const CLSID CLSID_MMDeviceEnumerator = __uuidof(MMDeviceEnumerator);
    const IID IID_IMMDeviceEnumerator = __uuidof(IMMDeviceEnumerator);

    CComPtr<IMMDeviceEnumerator> cpEnumeratorL = NULL;
    if (FAILED(CoCreateInstance(CLSID_MMDeviceEnumerator, NULL, CLSCTX_ALL, IID_IMMDeviceEnumerator, (void**)&cpEnumeratorL))) {
        cerr << "Unable to create device enumerator, more will fail later :(" << endl;
    }

    cpEnumerator = cpEnumeratorL;

    cpDeviceListener.Set(new DeviceListener(*this, cpEnumerator));
    InitDevices();

    pFocusListener = make_unique<FocusListener>(pJni);
}

void SndCtrl::InitDevices() {
    auto cpDevices = EnumAudioEndpoints(*cpEnumerator);
    NULLRETURN(cpDevices);
    auto count = GetCount(*cpDevices);
    for (UINT idx = 0; idx < count; idx++) {
        auto cpDevice = DeviceFromCollection(*cpDevices, idx);
        NULLCONTINUE(cpDevice);
        DeviceAdded(cpDevice);
    }

    for (int dataflow = eRender; dataflow < eAll; dataflow++) {
        auto df = (EDataFlow)dataflow;
        for (int role = 0; role < ERole_enum_count; role++) {
            CComPtr<IMMDevice> cpDevice = nullptr;
            ERole rl = (ERole)role;
            
            if (cpEnumerator->GetDefaultAudioEndpoint(df, rl, &cpDevice) == S_OK && cpDevice) {
                LPWSTR id = nullptr;
                cpDevice->GetId(&id);
    
                co_ptr<WCHAR> pId(id);
                SetDefaultDevice(id, df, rl);
            }
        }
    }
}

void SndCtrl::DeviceAdded(CComPtr<IMMDevice> cpDevice) {
    auto nameAndId = DeviceNameId(*cpDevice);
    wstring deviceId(nameAndId.id.get());

    float volume = 0;
    BOOL muted = 0;
    auto cpVolumeCtrl = GetVolumeControl(*cpDevice);
    if (cpVolumeCtrl) {
        cpVolumeCtrl->GetMasterVolumeLevelScalar(&volume);
        cpVolumeCtrl->GetMute(&muted);
    }

    JThread thread;
    if (*thread) {
        auto nameStr = thread.jstr(nameAndId.name.get());
        auto idStr = thread.jstr(nameAndId.id.get());
        auto dataFlow = getDataFlow(*cpDevice);
        auto jObj = pJni->CallObject(thread, "deviceAdded", "(Ljava/lang/String;Ljava/lang/String;FZI)Lcom/getpcpanel/cpp/AudioDevice;",
            nameStr, idStr, volume, muted, dataFlow
        );
        NULLRETURN(jObj);
        devices.insert({ deviceId, make_unique<AudioDevice>(deviceId, cpDevice, dataFlow, jObj)});
        thread.DoneWith(nameStr);
        thread.DoneWith(idStr);
        thread.DoneWith(jObj);
    }
}

void SndCtrl::DeviceRemoved(wstring deviceId) {
    thread detacher([&, deviceId]() {
        devices.erase(deviceId);
        JThread thread;
        if (*thread) {
            auto deviceIdStr = thread.jstr(deviceId.c_str());
            pJni->CallVoid(thread, "deviceRemoved", "(Ljava/lang/String;)V",
                deviceIdStr
            );
            thread.jstr(deviceIdStr);
        }
    });
    detacher.detach();
}

void SndCtrl::SetDeviceVolume(wstring deviceId, float volume) {
    auto found = devices.find(deviceId);
    if (found != devices.end()) {
        found->second->SetVolume(volume);
    }
}

void SndCtrl::MuteDevice(wstring deviceId, bool muted) {
    auto found = devices.find(deviceId);
    if (found != devices.end()) {
        found->second->Mute(muted);
    }
}

void SndCtrl::SetProcessVolume(wstring deviceId, int pid, float volume) {
    auto found = devices.find(deviceId);
    if (found != devices.end()) {
        found->second->SetProcessVolume(pid, volume);
    }
}

void SndCtrl::MuteProcess(wstring deviceId, int pid, bool muted) {
    auto found = devices.find(deviceId);
    if (found != devices.end()) {
        found->second->MuteProcess(pid, muted);
    }
}

void SndCtrl::SetFocusVolume(float volume) {
    auto pid = GetFocusProcessId();
    bool found = false;
    for (auto& entry : devices) {
        found = entry.second->SetProcessVolume(pid, volume) || found;
    }
    if (found) {
        return; // Volume was set, we are done.
    }

    // Not found by pid, find by name
    auto name = GetProcessName(pid);
    for (auto& dEntry : devices) {
        if (!dEntry.second->IsOutput()) {
            continue;
        }
        for (auto& sEntry : dEntry.second->GetSessions()) {
            for (auto& ssEntry : sEntry.second) {
                if (ssEntry->GetName() == name) {
                    ssEntry->SetVolume(volume);
                }
            }
        }
    }
}

void SndCtrl::UpdateDefaultDevice(wstring id, EDataFlow dataFlow, ERole role) {
    auto device = devices.find(id);
    if (device != devices.end()) {
        device->second->SetDefault(dataFlow, role);
    }
}

void SndCtrl::SetDefaultDevice(wstring id, EDataFlow dataFlow, ERole role) {
    JThread thread;
    if (*thread) {
        auto idStr = thread.jstr(id.c_str());
        pJni->CallVoid(thread, "setDefaultDevice", "(Ljava/lang/String;II)V",
            idStr, dataFlow, role
        );
        thread.jstr(idStr);
    }
}

CComPtr<IMMDeviceCollection> SndCtrl::EnumAudioEndpoints(IMMDeviceEnumerator& enumerator) {
    CComPtr<IMMDeviceCollection> cpDeviceCol;
    enumerator.EnumAudioEndpoints(eAll, DEVICE_STATE_ACTIVE, &cpDeviceCol);
    return cpDeviceCol;
}

UINT SndCtrl::GetCount(IMMDeviceCollection& collection) {
    UINT count;
    collection.GetCount(&count);
    return count;;
}

CComPtr<IMMDevice> SndCtrl::DeviceFromCollection(IMMDeviceCollection& collection, UINT idx) {
    CComPtr<IMMDevice> pDevice;
    collection.Item(idx, &pDevice);
    return pDevice;
}

SDeviceNameId SndCtrl::DeviceNameId(IMMDevice& device) {
    LPWSTR pwszID = NULL;
    if (device.GetId(&pwszID) != S_OK) {
        cout << "Unable to get device id" << endl;
    }

    IPropertyStore* pProps = NULL;
    device.OpenPropertyStore(STGM_READ, &pProps);
    auto props = CComPtr<IPropertyStore>(pProps);

    PROPVARIANT varName;
    PropVariantInit(&varName);

    // Get the endpoint's friendly-name property.
    if (pProps->GetValue(PKEY_Device_FriendlyName, &varName) != S_OK) {
        cout << "Unable to get name for " << pwszID << endl;
    }

    return SDeviceNameId{ co_ptr<WCHAR>(varName.pwszVal), co_ptr<WCHAR>(pwszID) };
}

CComPtr<IAudioEndpointVolume> SndCtrl::GetVolumeControl(IMMDevice& device) {
    CComPtr<IAudioEndpointVolume> pVol;
    device.Activate(__uuidof(IAudioEndpointVolume), CLSCTX_ALL, NULL, (void**)&pVol);
    return pVol;
}

void SndCtrl::TriggerAv() {
    static int count = 0;
    if (count++ == 0) {
        wcout << "Next click will break" << endl;
        return;
    }
    SndCtrl* pNull = nullptr;
    pNull->SetFocusVolume(123);
}

#include "pch.h"
#include "sndctrl.h"
#include "JniCaller.h"
#include "helpers.h"

unique_ptr<SndCtrl> pSndCtrl;

SndCtrl::SndCtrl(jobject obj) :
    jni(JniCaller::Create(obj)),
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
}

void SndCtrl::InitDevices() {
    auto cpDevices = EnumAudioEndpoints(*cpEnumerator);
    auto count = GetCount(*cpDevices);
    for (UINT idx = 0; idx < count; idx++) {
        auto cpDevice = DeviceFromCollection(*cpDevices, idx);
        DeviceAdded(cpDevice);
    }

    for (int dataflow = eRender; dataflow < eAll; dataflow++) {
        auto df = (EDataFlow)dataflow;
        for (int role = 0; role < ERole_enum_count; role++) {
            CComPtr<IMMDevice> cpDevice = nullptr;
            ERole rl = (ERole)role;
            cpEnumerator->GetDefaultAudioEndpoint(df, rl, &cpDevice);

            if (cpDevice) {
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
    auto volumeCtrl = GetVolumeControl(*cpDevice);
    if (volumeCtrl) {
        volumeCtrl->GetMasterVolumeLevelScalar(&volume);
        volumeCtrl->GetMute(&muted);
    }

    JThread thread;
    auto jObj = jni.CallObject("deviceAdded", "(Ljava/lang/String;Ljava/lang/String;FZI)Lcom/getpcpanel/cpp/AudioDevice;",
        thread.jstr(nameAndId.name.get()), thread.jstr(nameAndId.id.get()), volume, muted, getDataFlow(*cpDevice)
    );
    devices.insert({ deviceId, make_unique<AudioDevice>(deviceId, cpDevice, jObj)});
}

void SndCtrl::DeviceRemoved(wstring deviceId) {

    thread detacher([&, deviceId]() {
        devices.erase(deviceId);
        JThread thread;
        auto jObj = jni.CallObject("deviceRemoved", "(Ljava/lang/String;)V",
            thread.jstr(deviceId.c_str())
        );
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
        for (auto& sEntry : dEntry.second->GetSessions()) {
            if (sEntry.second->GetName() == name) {
                sEntry.second->SetVolume(volume);
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
    auto jObj = jni.CallObject("setDefaultDevice", "(Ljava/lang/String;II)V",
        thread.jstr(id.c_str()), dataFlow, role
    );
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
    device.GetId(&pwszID);

    IPropertyStore* pProps = NULL;
    device.OpenPropertyStore(STGM_READ, &pProps);
    auto props = CComPtr<IPropertyStore>(pProps);

    PROPVARIANT varName;
    PropVariantInit(&varName);

    // Get the endpoint's friendly-name property.
    pProps->GetValue(PKEY_Device_FriendlyName, &varName);

    return SDeviceNameId{ co_ptr<WCHAR>(varName.pwszVal), co_ptr<WCHAR>(pwszID) };
}

CComPtr<IAudioEndpointVolume> SndCtrl::GetVolumeControl(IMMDevice& device) {
    CComPtr<IAudioEndpointVolume> pVol;
    device.Activate(__uuidof(IAudioEndpointVolume), CLSCTX_ALL, NULL, (void**)&pVol);
    return pVol;
}

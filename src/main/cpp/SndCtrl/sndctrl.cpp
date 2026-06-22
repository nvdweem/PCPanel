#include "pch.h"
#include "sndctrl.h"
#include "JniCaller.h"
#include "helpers.h"
#include "roapi.h"
#include "winstring.h"

unique_ptr<SndCtrl> pSndCtrl;
wstring SndCtrl::MMDEVAPI_DEVICE_PREFIX = L"\\\\?\\SWD#MMDEVAPI#";
wstring SndCtrl::MMDEVAPI_RENDER_POSTFIX = L"#{e6327cad-dcec-4949-ae8a-991e976a79d2}";
wstring SndCtrl::MMDEVAPI_CAPTURE_POSTFIX = L"#{2eef81be-33fa-4800-9670-1cd474972c3f}";

SndCtrl::SndCtrl(JNIEnv* env, jobject obj) :
    pJni(new JniCaller(env, obj)),
    cpDeviceListener(nullptr),
    pPolicyConfigFactory(nullptr) {
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

    // Without an enumerator there is nothing to listen to or enumerate; registering the listener and
    // InitDevices would dereference a null enumerator. Focus tracking and the policy factory are
    // independent, so still set those up.
    if (cpEnumerator) {
        cpDeviceListener.Set(new DeviceListener(*this, cpEnumerator));
        InitDevices();
    }

    pFocusListener = make_unique<FocusListener>(pJni);
    BuildAudioPolicyConfigFactory();
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
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
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
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
    devices.erase(deviceId);
    JThread thread;
    if (*thread) {
        auto deviceIdStr = thread.jstr(deviceId.c_str());
        pJni->CallVoid(thread, "deviceRemoved", "(Ljava/lang/String;)V",
            deviceIdStr
        );
        thread.jstr(deviceIdStr);
    }
}

void SndCtrl::SetDeviceVolume(wstring deviceId, float volume) {
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
    auto found = devices.find(deviceId);
    if (found != devices.end()) {
        found->second->SetVolume(volume);
    }
}

void SndCtrl::MuteDevice(wstring deviceId, bool muted) {
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
    auto found = devices.find(deviceId);
    if (found != devices.end()) {
        found->second->Mute(muted);
    }
}

void SndCtrl::SetProcessVolume(wstring deviceId, int pid, float volume) {
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
    auto found = devices.find(deviceId);
    if (found != devices.end()) {
        found->second->SetProcessVolume(pid, volume);
    }
}

void SndCtrl::MuteProcess(wstring deviceId, int pid, bool muted) {
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
    auto found = devices.find(deviceId);
    if (found != devices.end()) {
        found->second->MuteProcess(pid, muted);
    }
}

void SndCtrl::SetFocusVolume(float volume) {
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
    auto pid = GetFocusProcessId();
    bool found = false;
    for (auto& entry : devices) {
        if (entry.second->IsOutput()) {
            found = entry.second->SetProcessVolume(pid, volume) || found;
        }
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
    std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
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

    PROPVARIANT varName;
    PropVariantInit(&varName);

    // OpenPropertyStore can fail (e.g. a device disappearing mid-enumeration); guard the null store
    // rather than dereferencing it. Writing into the CComPtr out-param adopts the single reference it
    // returns without an extra AddRef.
    CComPtr<IPropertyStore> props;
    if (SUCCEEDED(device.OpenPropertyStore(STGM_READ, &props)) && props) {
        // Get the endpoint's friendly-name property.
        if (props->GetValue(PKEY_Device_FriendlyName, &varName) != S_OK) {
            cout << "Unable to get name for " << pwszID << endl;
        }
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

void SndCtrl::BuildAudioPolicyConfigFactory() {
    static const WCHAR* className = L"Windows.Media.Internal.AudioPolicyConfig";
    const UINT32 clen = wcslen(className);

    HSTRING hClassName = NULL;
    HSTRING_HEADER header;
    HRESULT hr = WindowsCreateStringReference(className, clen, &header, &hClassName);
    if (FAILED(hr)) {
        WindowsDeleteString(hClassName);
        return;
    }

    hr = RoGetActivationFactory(hClassName, __uuidof(IAudioPolicyConfigFactory), (void**)&pPolicyConfigFactory);
    if (hr == E_NOINTERFACE) {
        hr = RoGetActivationFactory(hClassName, __uuidof(IAudioPolicyConfigFactoryLegacy), (void**)&pPolicyConfigFactory);
    }
    if (SUCCEEDED(hr)) {
        cout << "IAudioPolicyConfigFactory constructed successfully" << endl;
    } else {
        cerr << "Unable to retrieve IAudioPolicyConfigFactory" << endl;
    }
    WindowsDeleteString(hClassName);
}

bool SndCtrl::SetPersistedDefaultAudioEndpoint(int pid, EDataFlow flow, wstring deviceId) {
    if (pPolicyConfigFactory == nullptr) {
        return false;
    }
    HSTRING hDeviceId = nullptr;

    if (!deviceId.empty()) {
        wstring fullDeviceId(MMDEVAPI_DEVICE_PREFIX + deviceId + (flow == eRender ? MMDEVAPI_RENDER_POSTFIX : MMDEVAPI_CAPTURE_POSTFIX));
        auto hr = WindowsCreateString(fullDeviceId.c_str(), fullDeviceId.length(), &hDeviceId);
        if (FAILED(hr)) {
            return false;
        }
    }

    auto hrCo = pPolicyConfigFactory->SetPersistedDefaultAudioEndpoint(pid, flow, eConsole, hDeviceId);
    auto hrMM = pPolicyConfigFactory->SetPersistedDefaultAudioEndpoint(pid, flow, eMultimedia, hDeviceId);
    return SUCCEEDED(hrCo) && SUCCEEDED(hrMM);
}

wstring SndCtrl::GetPersistedDefaultAudioEndpoint(int pid, EDataFlow flow) {
    if (pPolicyConfigFactory == nullptr) {
        return wstring();
    }
    HSTRING hDeviceId = nullptr;
    auto hrMM = pPolicyConfigFactory->GetPersistedDefaultAudioEndpoint(pid, flow, eMultimedia | eConsole, &hDeviceId);

    UINT32 len;
    return WindowsGetStringRawBuffer(hDeviceId, &len);
}

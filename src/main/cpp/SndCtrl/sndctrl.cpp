#include "pch.h"
#include "sndctrl.h"
#include "JniCaller.h"
#include "helpers.h"
#include "roapi.h"
#include "winstring.h"

std::atomic<SndCtrl*> pSndCtrl{ nullptr };
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

/**
 * Destroys an AudioDevice on a thread of its own, never on the caller's.
 *
 * Releasing the device releases its IAudioEndpointVolume, and that Release ends in
 * MMDevApi!UnregisterMediaCallback -> AXB::WaitForOperations, which waits for every media notification
 * callback in flight to finish. Called from inside IMMNotificationClient::OnDeviceStateChanged, one of
 * those callbacks is the caller itself, so it waits on its own return and never comes back -- taking
 * g_audioMutex with it if it holds it, and with it every dial and button, since all of them are run by
 * one command thread that needs the mutex for any volume call. AudioDevice::SessionRemoved already does
 * this for sessions, and for the same reason.
 *
 * The thread owns the device outright, so it depends on nothing that could outlive it.
 */
static void ReleaseOffCallbackThread(std::unique_ptr<AudioDevice> device) {
    if (device) {
        std::thread([d = std::move(device)]() mutable { d.reset(); }).detach();
    }
}

/**
 * Builds the device and registers it, doing every slow part outside g_audioMutex.
 *
 * The mutex guards the device map, but it is also the mutex every volume, mute and default-device call
 * from Java has to take. Building a device is not quick: it reads COM properties, activates the endpoint
 * and its session manager, enumerates the existing sessions, and calls up into Java for each one -- which
 * runs application code. Doing that under the mutex means a single slow endpoint blocks all volume
 * control, and Windows delivers these notifications in bursts (resuming from sleep, with the audio
 * service still restarting, is the worst of them). Windows also documents an IMMNotificationClient
 * callback as no place to block. So the mutex is taken only to publish the finished device.
 */
void SndCtrl::DeviceAdded(CComPtr<IMMDevice> cpDevice) {
    // The endpoint-notification path resolves the device by id, which fails when it disappears between
    // the notification and the lookup.
    NULLRETURN(cpDevice);

    auto nameAndId = DeviceNameId(*cpDevice);
    // The id keys the device map and is the handle Java addresses the device by, so there is nothing
    // to register without it. The friendly name is optional and renders empty when absent.
    NULLRETURN(nameAndId.id);
    wstring deviceId(nameAndId.id.get());

    {
        // Already known: nothing to build. Re-adding would drop the live device and its listeners.
        std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
        if (devices.find(deviceId) != devices.end()) {
            return;
        }
    }

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
        auto jObj = pJni->CallObject(thread, "deviceAdded", "(Ljava/lang/String;Ljava/lang/String;FZI)Lcom/getpcpanel/integration/volume/platform/AudioDevice;",
            nameStr, idStr, volume, muted, dataFlow
        );
        NULLRETURN(jObj);
        auto device = make_unique<AudioDevice>(deviceId, cpDevice, dataFlow, jObj);
        {
            std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
            // insert (not insert_or_assign): if another notification won the race, keep the registered
            // device. The loser is released below.
            devices.insert({ deviceId, std::move(device) });
        }
        ReleaseOffCallbackThread(std::move(device));
        thread.DoneWith(nameStr);
        thread.DoneWith(idStr);
        thread.DoneWith(jObj);
    }
}

void SndCtrl::DeviceRemoved(wstring deviceId) {
    // Take the device out under the mutex, then tell Java without holding it: the callback runs
    // application code, which the volume calls waiting on this mutex should not be behind.
    std::unique_ptr<AudioDevice> removed;
    {
        std::lock_guard<std::recursive_mutex> lock(g_audioMutex);
        auto found = devices.find(deviceId);
        if (found != devices.end()) {
            removed = std::move(found->second);
            devices.erase(found);
        }
    }
    ReleaseOffCallbackThread(std::move(removed));

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
    UINT count = 0;
    if (FAILED(collection.GetCount(&count))) {
        return 0;
    }
    return count;
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

    // Only VT_LPWSTR carries a string in pwszVal. The property value comes from the device driver, so
    // any other type would be reinterpreted as a pointer here and then both dereferenced and freed.
    // Adopting pwszVal takes over its allocation, which is why only the non-string case clears.
    LPWSTR pwszName = nullptr;
    if (varName.vt == VT_LPWSTR) {
        pwszName = varName.pwszVal;
    } else {
        PropVariantClear(&varName);
    }

    return SDeviceNameId{ co_ptr<WCHAR>(pwszName), co_ptr<WCHAR>(pwszID) };
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
        auto hr = WindowsCreateString(fullDeviceId.c_str(), static_cast<UINT32>(fullDeviceId.length()), &hDeviceId);
        if (FAILED(hr)) {
            return false;
        }
    }

    auto hrCo = pPolicyConfigFactory->SetPersistedDefaultAudioEndpoint(pid, flow, eConsole, hDeviceId);
    auto hrMM = pPolicyConfigFactory->SetPersistedDefaultAudioEndpoint(pid, flow, eMultimedia, hDeviceId);
    WindowsDeleteString(hDeviceId); // a no-op on the null handle that an empty device id leaves
    return SUCCEEDED(hrCo) && SUCCEEDED(hrMM);
}

wstring SndCtrl::GetPersistedDefaultAudioEndpoint(int pid, EDataFlow flow) {
    if (pPolicyConfigFactory == nullptr) {
        return wstring();
    }
    HSTRING hDeviceId = nullptr;
    if (FAILED(pPolicyConfigFactory->GetPersistedDefaultAudioEndpoint(pid, flow, eMultimedia | eConsole, &hDeviceId))) {
        return wstring();
    }

    UINT32 len = 0;
    auto* raw = WindowsGetStringRawBuffer(hDeviceId, &len);
    wstring result = raw ? wstring(raw, len) : wstring();
    WindowsDeleteString(hDeviceId);
    return result;
}

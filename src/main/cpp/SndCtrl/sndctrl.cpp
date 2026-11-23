#include "pch.h"
#include "sndctrl.h"
#include "JniCaller.h"
#include "helpers.h"
#include "roapi.h"
#include "winstring.h"
#include <condition_variable>
#include <deque>
#include <future>
#include <set>

std::atomic<SndCtrl*> pSndCtrl{ nullptr };

/**
 * How long a disconnected session's endpoint is left to settle before its sessions are read again. Measured on a
 * Bluetooth headset reconnecting: Windows renegotiated the format about 150 ms after the endpoint came back, which
 * invalidated every session handle, and announced the sessions again up to 1.6 s after that. Reading earlier can take
 * a handle that is invalidated in turn; the next volume call then finds it so and reads them once more.
 */
constexpr auto SESSION_REBUILD_SETTLE = std::chrono::seconds(2);

namespace {
/**
 * The one thread that re-reads devices' sessions, in a multithreaded apartment for the life of the process, so the
 * session handles it takes belong to an apartment that stays. The command thread is a Java thread whose COM state is
 * not ours to set, and a notification callback may not block. Never destroyed, like SndCtrl itself: its thread waits
 * on it until the process ends.
 */
class SessionRebuildWorker {
public:
    static SessionRebuildWorker& Get() {
        static auto* worker = new SessionRebuildWorker();
        return *worker;
    }

    std::future<void> Post(shared_ptr<AudioDevice> device) {
        std::promise<void> done;
        auto result = done.get_future();
        {
            std::lock_guard<std::mutex> lock(mutex);
            queue.emplace_back(std::move(device), std::move(done));
        }
        wake.notify_one();
        return result;
    }

private:
    std::mutex mutex;
    std::condition_variable wake;
    std::deque<std::pair<shared_ptr<AudioDevice>, std::promise<void>>> queue;

    SessionRebuildWorker() {
        std::thread([this]() { Run(); }).detach();
    }

    void Run() {
        if (FAILED(CoInitializeEx(nullptr, COINIT_MULTITHREADED))) {
            cerr << "SessionRebuildWorker: unable to enter the multithreaded apartment" << endl;
        }
        JThread thread; // attached to the JVM once, for the thread's lifetime
        for (;;) {
            std::unique_lock<std::mutex> lock(mutex);
            wake.wait(lock, [this]() { return !queue.empty(); });
            auto job = std::move(queue.front());
            queue.pop_front();
            lock.unlock();

            job.first->RebuildSessions();
            job.second.set_value();
        }
    }
};

std::mutex rebuildRequestsMutex;
std::set<wstring> rebuildRequests;
}

void RequestSessionRebuild(const wstring& deviceId) {
    {
        std::lock_guard<std::mutex> lock(rebuildRequestsMutex);
        if (!rebuildRequests.insert(deviceId).second) {
            return; // one is already waiting; a session disconnect arrives once per session
        }
    }
    std::thread([deviceId]() {
        std::this_thread::sleep_for(SESSION_REBUILD_SETTLE);
        {
            // Cleared before the rebuild, so a disconnect during it asks for another.
            std::lock_guard<std::mutex> lock(rebuildRequestsMutex);
            rebuildRequests.erase(deviceId);
        }
        auto* ctrl = pSndCtrl.load();
        if (ctrl) {
            ctrl->RebuildSessions(deviceId);
        }
    }).detach();
}

void SndCtrl::RebuildSessions(const wstring& deviceId) {
    shared_ptr<AudioDevice> device;
    {
        auto lock = lockAudioForJavaCall();
        if (!lock.owns_lock()) {
            cerr << "RebuildSessions: audio lock unavailable, skipping" << endl;
            return;
        }
        auto found = devices.find(deviceId);
        if (found == devices.end()) {
            return;
        }
        device = found->second;
    }
    SessionRebuildWorker::Get().Post(std::move(device));
}

template<typename Call> void SndCtrl::ApplyToSessions(const char* what, const wstring& deviceId, Call call) {
    shared_ptr<AudioDevice> device;
    {
        auto lock = lockAudioForJavaCall();
        if (!lock.owns_lock()) {
            cerr << what << ": audio lock unavailable, skipping" << endl;
            return;
        }
        auto found = devices.find(deviceId);
        if (found == devices.end() || call(*found->second) != SessionCall::Invalidated) {
            return;
        }
        device = found->second;
    }

    // Re-read without the audio lock, which the rebuild takes itself, and bounded like the lock: this is the thread
    // that runs every dial and button.
    if (SessionRebuildWorker::Get().Post(device).wait_for(AUDIO_LOCK_TIMEOUT) != std::future_status::ready) {
        cerr << what << ": re-reading the sessions took too long, skipping" << endl;
        return;
    }
    auto lock = lockAudioForJavaCall();
    if (lock.owns_lock()) {
        call(*device);
    }
}
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

void SndCtrl::Ready() {
    JThread thread;
    if (*thread) {
        pJni->CallVoid(thread, "audioReady", "()V");
    }
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
 * The thread holds its own reference, so it depends on nothing that could outlive it. When a session rebuild still
 * holds one, the device goes when that rebuild lets go of it, on the rebuild worker or the command thread, neither of
 * which is a notification callback.
 */
static void ReleaseOffCallbackThread(shared_ptr<AudioDevice> device) {
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
        std::lock_guard<std::recursive_timed_mutex> lock(g_audioMutex);
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
        auto interfaceNameStr = thread.jstr(nameAndId.interfaceName.get());
        auto dataFlow = getDataFlow(*cpDevice);
        auto jObj = pJni->CallObject(thread, "deviceAdded", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;FZI)Lcom/getpcpanel/integration/volume/platform/AudioDevice;",
            nameStr, idStr, interfaceNameStr, volume, muted, dataFlow
        );
        thread.DoneWith(interfaceNameStr);
        NULLRETURN(jObj);
        auto device = make_shared<AudioDevice>(deviceId, cpDevice, dataFlow, jObj);
        {
            std::lock_guard<std::recursive_timed_mutex> lock(g_audioMutex);
            // insert (not insert_or_assign): if another notification won the race, keep the registered
            // device. This reference is released below, which releases the loser.
            devices.insert({ deviceId, device });
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
    shared_ptr<AudioDevice> removed;
    {
        std::lock_guard<std::recursive_timed_mutex> lock(g_audioMutex);
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
    auto lock = lockAudioForJavaCall();
    if (!lock.owns_lock()) {
        cerr << "SetDeviceVolume: audio lock unavailable, skipping" << endl;
        return;
    }
    auto found = devices.find(deviceId);
    if (found != devices.end()) {
        found->second->SetVolume(volume);
    }
}

void SndCtrl::MuteDevice(wstring deviceId, bool muted) {
    auto lock = lockAudioForJavaCall();
    if (!lock.owns_lock()) {
        cerr << "MuteDevice: audio lock unavailable, skipping" << endl;
        return;
    }
    auto found = devices.find(deviceId);
    if (found != devices.end()) {
        found->second->Mute(muted);
    }
}

void SndCtrl::SetProcessVolume(wstring deviceId, int pid, float volume) {
    ApplyToSessions("SetProcessVolume", deviceId, [pid, volume](AudioDevice& device) { return device.SetProcessVolume(pid, volume); });
}

void SndCtrl::MuteProcess(wstring deviceId, int pid, bool muted) {
    ApplyToSessions("MuteProcess", deviceId, [pid, muted](AudioDevice& device) { return device.MuteProcess(pid, muted); });
}

void SndCtrl::SetFocusVolume(float volume) {
    auto lock = lockAudioForJavaCall();
    if (!lock.owns_lock()) {
        cerr << "SetFocusVolume: audio lock unavailable, skipping" << endl;
        return;
    }
    auto pid = GetFocusProcessId();
    bool found = false;
    for (auto& entry : devices) {
        if (entry.second->IsOutput()) {
            auto result = entry.second->SetProcessVolume(pid, volume);
            if (result == SessionCall::Invalidated) {
                // Re-read for the next turn of the dial rather than wait here with the audio lock held.
                SessionRebuildWorker::Get().Post(entry.second);
            }
            found = result != SessionCall::NotFound || found;
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
    auto lock = lockAudioForJavaCall();
    if (!lock.owns_lock()) {
        cerr << "UpdateDefaultDevice: audio lock unavailable, skipping" << endl;
        return;
    }
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

co_ptr<WCHAR> StringProperty(IPropertyStore& props, const PROPERTYKEY& key) {
    PROPVARIANT value;
    PropVariantInit(&value);
    if (props.GetValue(key, &value) != S_OK) {
        return co_ptr<WCHAR>(nullptr);
    }
    // Only VT_LPWSTR carries a string in pwszVal. The property value comes from the device driver, so
    // any other type would be reinterpreted as a pointer here and then both dereferenced and freed.
    // Adopting pwszVal takes over its allocation, which is why only the non-string case clears.
    if (value.vt == VT_LPWSTR) {
        return co_ptr<WCHAR>(value.pwszVal);
    }
    PropVariantClear(&value);
    return co_ptr<WCHAR>(nullptr);
}

SDeviceNameId SndCtrl::DeviceNameId(IMMDevice& device) {
    LPWSTR pwszID = NULL;
    if (device.GetId(&pwszID) != S_OK) {
        cout << "Unable to get device id" << endl;
    }

    co_ptr<WCHAR> name(nullptr);
    co_ptr<WCHAR> interfaceName(nullptr);
    // OpenPropertyStore can fail (e.g. a device disappearing mid-enumeration); guard the null store
    // rather than dereferencing it. Writing into the CComPtr out-param adopts the single reference it
    // returns without an extra AddRef.
    CComPtr<IPropertyStore> props;
    if (SUCCEEDED(device.OpenPropertyStore(STGM_READ, &props)) && props) {
        name = StringProperty(*props, PKEY_Device_FriendlyName);
        if (!name) {
            cout << "Unable to get name for device" << endl;
        }
        interfaceName = StringProperty(*props, PKEY_DeviceInterface_FriendlyName);
    }

    return SDeviceNameId{ std::move(name), co_ptr<WCHAR>(pwszID), std::move(interfaceName) };
}

/**
 * Looks the endpoint up on a thread of its own, in a multithreaded apartment it sets up and tears down itself, so the
 * calling (Java) thread's COM state is never touched and SndCtrl need not be running. Windows keeps an endpoint it
 * has replaced (a driver reinstalled, the device moved to another port) as not present, properties and all, which is
 * what lets the app recognise the replacement.
 */
wstring DescribeEndpoint(const wstring& id) {
    wstring result;
    std::thread([&id, &result]() {
        if (FAILED(CoInitializeEx(nullptr, COINIT_MULTITHREADED))) {
            return;
        }
        {
            CComPtr<IMMDeviceEnumerator> cpEnumerator;
            if (SUCCEEDED(CoCreateInstance(__uuidof(MMDeviceEnumerator), NULL, CLSCTX_ALL, __uuidof(IMMDeviceEnumerator), (void**)&cpEnumerator)) && cpEnumerator) {
                CComPtr<IMMDevice> cpDevice;
                CComPtr<IPropertyStore> props;
                if (SUCCEEDED(cpEnumerator->GetDevice(id.c_str(), &cpDevice)) && cpDevice
                    && SUCCEEDED(cpDevice->OpenPropertyStore(STGM_READ, &props)) && props) {
                    auto name = StringProperty(*props, PKEY_Device_FriendlyName);
                    auto interfaceName = StringProperty(*props, PKEY_DeviceInterface_FriendlyName);
                    result = wstring(name ? name.get() : L"") + L"\n" + (interfaceName ? interfaceName.get() : L"") + L"\n"
                             + to_wstring((int) getDataFlow(*cpDevice));
                }
            }
        }
        CoUninitialize();
    }).join();
    return result;
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

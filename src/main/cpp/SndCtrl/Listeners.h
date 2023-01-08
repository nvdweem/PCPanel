#pragma once
#include <unordered_map>
#include "JniCaller.h"
#include <thread>

EDataFlow getDataFlow(IMMDevice& device);

class AudioSession;
class AudioSessionListener;
class DeviceVolumeListener;
class SessionListener;
class SndCtrl;

struct CoRelease {
    void operator()(LPVOID itm) {
        if (itm != NULL) {
            CoTaskMemFree(itm);
        }
    }
};
template<typename T> using co_ptr = std::unique_ptr<T, CoRelease>;

template<typename T> class StoppingHandle {
private:
    CComPtr<T> cpListener;

public:
    StoppingHandle() : cpListener(nullptr) {}
    StoppingHandle(CComPtr<T> cpListener) : cpListener(cpListener) {
        Start();
    }
    ~StoppingHandle() {
        Stop();
    }
    void Set(CComPtr<T> cpListener) {
        Stop();
        this->cpListener = cpListener;
        Start();
    }

    T* operator->() { return cpListener; }

private:
    void Stop() {
        if (cpListener != nullptr) {
            cpListener->Stop();
            cpListener = nullptr;
        }
    }

    void Start() {
        if (cpListener != nullptr) {
            cpListener->Start();
        }
    }
};

class Listener {
public:
    LONG m_cRefAll;

public:
    Listener() : m_cRefAll(0) {}
    virtual ~Listener() {}
    virtual void Start() = 0;
    virtual void Stop() = 0;

    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) {
        if (IID_IUnknown == riid) {
            AddRef();
            *ppv = this;
            return S_OK;
        }
        else {
            *ppv = nullptr;
            return E_NOINTERFACE;
        }
    }

    ULONG STDMETHODCALLTYPE AddRef() {
        return InterlockedIncrement(&m_cRefAll);
    }

    ULONG STDMETHODCALLTYPE Release() {
        ULONG ulRef = InterlockedDecrement(&m_cRefAll);
        if (0 == ulRef) {
            delete this;
        }
        return ulRef;
    }
};

class DeviceListenerCB {
public:
    virtual void SetDefaultDevice(wstring id, EDataFlow dataFlow, ERole role) = 0;
    virtual void DeviceAdded(CComPtr<IMMDevice> pDevice) = 0;
    virtual void DeviceRemoved(wstring pDevice) = 0;
};

class DeviceListener : public Listener, public IMMNotificationClient {
private:
    DeviceListenerCB& ctrl;
    CComPtr<IMMDeviceEnumerator> cpEnumerator;
public:
    DeviceListener(DeviceListenerCB& ctrl, CComPtr<IMMDeviceEnumerator> e) :
        ctrl(ctrl), cpEnumerator(e) {
    }

    virtual void Start() {
        NULLRETURN(cpEnumerator)
        cpEnumerator->RegisterEndpointNotificationCallback(this);
    }

    virtual void Stop() {
        NULLRETURN(cpEnumerator);
        cpEnumerator->UnregisterEndpointNotificationCallback(this);
    }

    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) {
        if (__uuidof(IMMNotificationClient) == riid)
        {
            AddRef();
            *ppv = (IMMNotificationClient*)this;
            return S_OK;
        }
        return Listener::QueryInterface(riid, ppv);
    }

    virtual HRESULT STDMETHODCALLTYPE OnDeviceStateChanged(LPCWSTR pwstrDeviceId, DWORD dwNewState) { 
        if (dwNewState != DEVICE_STATE_ACTIVE) {
            return OnDeviceRemoved(pwstrDeviceId);
        }
        else {
            return OnDeviceAdded(pwstrDeviceId);
        }
    }

    virtual HRESULT STDMETHODCALLTYPE OnDefaultDeviceChanged(EDataFlow flow, ERole role, LPCWSTR pwstrDefaultDeviceId) {
        if (pwstrDefaultDeviceId == nullptr) {
            wstring empty;
            ctrl.SetDefaultDevice(empty, flow, role);
        } else {
            wstring tempStr(pwstrDefaultDeviceId);
            ctrl.SetDefaultDevice(tempStr, flow, role);
        }
        return S_OK;
    }
    virtual HRESULT STDMETHODCALLTYPE OnPropertyValueChanged(LPCWSTR pwstrDeviceId, const PROPERTYKEY key) { return S_OK; }
    virtual HRESULT STDMETHODCALLTYPE OnDeviceAdded(LPCWSTR pwstrDeviceId) {
        CComPtr<IMMDevice> cpDevice;
        cpEnumerator->GetDevice(pwstrDeviceId, &cpDevice);
        ctrl.DeviceAdded(cpDevice);
        return S_OK;
    }

    virtual HRESULT STDMETHODCALLTYPE OnDeviceRemoved(LPCWSTR pwstrDeviceId) {
        wstring tempStr(pwstrDeviceId);
        ctrl.DeviceRemoved(tempStr);
        return S_OK;
    }

    ULONG STDMETHODCALLTYPE AddRef() override { return Listener::AddRef(); }
    ULONG STDMETHODCALLTYPE Release() override { return Listener::Release(); }

private:

};

class DeviceVolumeListener : public Listener, public IAudioEndpointVolumeCallback {
private:
    JniCaller& jni;
    CComPtr<IAudioEndpointVolume> pVolume;
public:
    DeviceVolumeListener(CComPtr<IAudioEndpointVolume> pVolume, JniCaller& jni) : pVolume(pVolume), jni(jni) {
    }
    virtual void Start() {
        NULLRETURN(pVolume);
        pVolume->RegisterControlChangeNotify(this);
    }
    virtual void Stop() {
        NULLRETURN(pVolume);
        pVolume->UnregisterControlChangeNotify(this);
    }

    virtual HRESULT STDMETHODCALLTYPE OnNotify(PAUDIO_VOLUME_NOTIFICATION_DATA pNotify) {
        JThread env;
        if (*env) {
            jni.CallVoid(env, "setState", "(FZ)V", pNotify->fMasterVolume, pNotify->bMuted);
        }
        return S_OK;
    }

    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override {
        if (__uuidof(IAudioEndpointVolumeCallback) == riid) {
            AddRef();
            *ppv = this;
            return S_OK;
        }
        return Listener::QueryInterface(riid, ppv);
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return Listener::AddRef(); }
    ULONG STDMETHODCALLTYPE Release() override { return Listener::Release(); }
};

class SessionListenerCB {
public:
    virtual void OnNewSession(IAudioSessionControl* pNewSession) = 0;
};

class SessionListener : public Listener, public IAudioSessionNotification {
private:
    SessionListenerCB& cb;
    CComPtr<IAudioSessionManager2> sessionManager;

public:
    SessionListener(SessionListenerCB& cb, CComPtr<IAudioSessionManager2> sessionManager)
        : cb(cb), sessionManager(sessionManager) {
    }
    virtual void Start() {
        NULLRETURN(sessionManager)
        sessionManager->RegisterSessionNotification(this);
    }
    virtual void Stop() {
        NULLRETURN(sessionManager)
        sessionManager->UnregisterSessionNotification(this);
    }

    HRESULT STDMETHODCALLTYPE OnSessionCreated(IAudioSessionControl* pNewSession) {
        if (pNewSession) {
            cb.OnNewSession(pNewSession);
        }
        return S_OK;
    }

    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override { 
        if (__uuidof(IAudioSessionNotification) == riid) {
            AddRef();
            *ppv = this;
            return S_OK;
        }
        return Listener::QueryInterface(riid, ppv); 
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return Listener::AddRef(); }
    ULONG STDMETHODCALLTYPE Release() override { return Listener::Release(); }
};

class AudioSessionListenerCB {
public:
    virtual void SessionRemoved(AudioSession& session) = 0;
};

class AudioSessionListener : public Listener, public IAudioSessionEvents {
private:
    JniCaller jni;
    AudioSessionListenerCB& callback;
    CComPtr<IAudioSessionControl> sessionControl;
    AudioSession& session;

public:
    AudioSessionListener(CComPtr<IAudioSessionControl> sessionControl, AudioSession& session, AudioSessionListenerCB& callback, jobject obj)
        : sessionControl(sessionControl), session(session), callback(callback), jni(JniCaller::Create(obj)) {
    }
    virtual void Start() {
        NULLRETURN(sessionControl);
        sessionControl->RegisterAudioSessionNotification(this);
    }
    virtual void Stop() {
        NULLRETURN(sessionControl);
        sessionControl->UnregisterAudioSessionNotification(this);
    }

    virtual HRESULT STDMETHODCALLTYPE OnDisplayNameChanged(LPCWSTR NewDisplayName, LPCGUID EventContext) { 
        JThread env;
        if (*env) {
            auto dnStr = env.jstr(NewDisplayName);
            jni.CallObject(env, "name", "(Ljava/lang/String;)Lcom/getpcpanel/cpp/AudioSession;", dnStr);
            env.jstr(dnStr);
        }
        return S_OK; 
    }
    virtual HRESULT STDMETHODCALLTYPE OnIconPathChanged(LPCWSTR NewIconPath, LPCGUID EventContext) {
        JThread env;
        if (*env) {
            auto iconStr = env.jstr(NewIconPath);
            jni.CallObject(env, "icon", "(Ljava/lang/String;)Lcom/getpcpanel/cpp/AudioSession;", iconStr);
            env.jstr(iconStr);
        }
        return S_OK;
    }
    virtual HRESULT STDMETHODCALLTYPE OnChannelVolumeChanged(DWORD ChannelCount, float NewChannelVolumeArray[], DWORD ChangedChannel, LPCGUID EventContext) { return S_OK; }
    virtual HRESULT STDMETHODCALLTYPE OnGroupingParamChanged(LPCGUID NewGroupingParam, LPCGUID EventContext) { return S_OK; }
    virtual HRESULT STDMETHODCALLTYPE OnSessionDisconnected(AudioSessionDisconnectReason DisconnectReason) { return S_OK; }
    virtual HRESULT STDMETHODCALLTYPE OnSimpleVolumeChanged(float NewVolume, BOOL NewMute, LPCGUID EventContext) {
        JThread env;
        if (*env) {
            jni.CallObject(env, "volume", "(F)Lcom/getpcpanel/cpp/AudioSession;", NewVolume);
            jni.CallObject(env, "muted", "(Z)Lcom/getpcpanel/cpp/AudioSession;", NewMute);
        }
        return S_OK;
    }
    virtual HRESULT STDMETHODCALLTYPE OnStateChanged(AudioSessionState NewState) {
        if (NewState == AudioSessionStateExpired) {
            callback.SessionRemoved(session);
        }
        return S_OK;
    }

    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override { 
        if (__uuidof(IAudioSessionEvents) == riid) {
            AddRef();
            *ppv = this;
            return S_OK;
        }
        return Listener::QueryInterface(riid, ppv); 
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return Listener::AddRef(); }
    ULONG STDMETHODCALLTYPE Release() override { return Listener::Release(); }
};

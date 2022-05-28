#pragma once
#include "pch.h"

class Listener {
public:
    LONG m_cRefAll;

public:
    Listener() : m_cRefAll(0) {}
    virtual ~Listener() {}

    // IUnknown
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) {
        if (IID_IUnknown == riid) {
            AddRef();
            *ppv = (IUnknown*)this;
        }
        else if (__uuidof(IAudioSessionNotification) == riid) {
            AddRef();
            *ppv = (IAudioSessionNotification*)this;
        }
        else {
            *ppv = NULL;
            return E_NOINTERFACE;
        }
        return S_OK;
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



class DeviceListener : public Listener, public IMMNotificationClient {
private:
    CComPtr<IMMDeviceEnumerator> pEnumerator;
public:
    DeviceListener(CComPtr<IMMDeviceEnumerator> e) {
        e->RegisterEndpointNotificationCallback(this);
    }
    void Stop() {
        pEnumerator->UnregisterEndpointNotificationCallback(this);
    }

    virtual HRESULT STDMETHODCALLTYPE OnDeviceStateChanged(LPCWSTR pwstrDeviceId, DWORD dwNewState) { return S_OK; }
    virtual HRESULT STDMETHODCALLTYPE OnDefaultDeviceChanged(EDataFlow flow, ERole role, LPCWSTR pwstrDefaultDeviceId) {
        wstring tempStr(pwstrDefaultDeviceId);
        defaultDeviceChanged(&tempStr[0], flow, role);
        return S_OK;
    }
    virtual HRESULT STDMETHODCALLTYPE OnPropertyValueChanged(LPCWSTR pwstrDeviceId, const PROPERTYKEY key) { return S_OK; }
    virtual HRESULT STDMETHODCALLTYPE OnDeviceAdded(LPCWSTR pwstrDeviceId) {
        CComPtr<IMMDevice> pDevice;
        pEnumerator->GetDevice(pwstrDeviceId, &pDevice);
        DeviceAdded(pDevice);
        return S_OK;
    }
    virtual HRESULT STDMETHODCALLTYPE OnDeviceRemoved(LPCWSTR pwstrDeviceId) {
        auto entry = deviceSessionManagers.find(pwstrDeviceId);
        if (entry != deviceSessionManagers.end()) {
            entry->second.first->Stop();
            entry->second.second->Stop();
            deviceSessionManagers.erase(entry);
        }
        return S_OK;
    }

    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override { return Listener::QueryInterface(riid, ppv); }
    ULONG STDMETHODCALLTYPE AddRef() override { return Listener::AddRef(); }
    ULONG STDMETHODCALLTYPE Release() override { return Listener::Release(); }
};

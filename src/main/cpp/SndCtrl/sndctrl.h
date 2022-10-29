#pragma once
#include "AudioDevice.h"
#include "JniCaller.h"
#include "Listeners.h"
#include "FocusListener.h"

struct SDeviceNameId {
    co_ptr<WCHAR> name;
    co_ptr<WCHAR> id;
};

class SndCtrl : public DeviceListenerCB
{
private:
    shared_ptr<JniCaller> pJni;
    unique_ptr<FocusListener> pFocusListener;
    CComPtr<IMMDeviceEnumerator> cpEnumerator;
    unordered_map<wstring, unique_ptr<AudioDevice>> devices;
    StoppingHandle<DeviceListener> cpDeviceListener;

public:
    SndCtrl(JNIEnv* env, jobject obj);

    virtual void SetDefaultDevice(wstring id, EDataFlow dataFlow, ERole role);
    virtual void DeviceAdded(CComPtr<IMMDevice> cpDevice);
    virtual void DeviceRemoved(wstring deviceId);

    // Called from Java
    void SetDeviceVolume(wstring deviceId, float volume);
    void MuteDevice(wstring deviceId, bool muted);
    void SetProcessVolume(wstring deviceId, int pid, float volume);
    void MuteProcess(wstring deviceId, int pid, bool muted);
    void SetFocusVolume(float volume);
    void UpdateDefaultDevice(wstring id, EDataFlow dataFlow, ERole role);
    void TriggerAv();

private:
    void InitDevices();

    CComPtr<IMMDeviceCollection> EnumAudioEndpoints(IMMDeviceEnumerator& enumerator);
    UINT GetCount(IMMDeviceCollection& collection);
    CComPtr<IMMDevice> DeviceFromCollection(IMMDeviceCollection& collection, UINT idx);

    SDeviceNameId DeviceNameId(IMMDevice& device);
    CComPtr<IAudioEndpointVolume> GetVolumeControl(IMMDevice& device);
    CComPtr<IAudioSessionManager2> Activate(IMMDevice& device);
};

extern unique_ptr<SndCtrl> pSndCtrl;

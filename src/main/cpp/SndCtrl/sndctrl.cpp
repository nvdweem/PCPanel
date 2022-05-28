#include "pch.h"
#include "sndctrl.h"
#include "JniCaller.h"

unique_ptr<SndCtrl> pSndCtrl;

SndCtrl::SndCtrl(jobject obj)
    : jni(JniCaller::Create(obj))
{
    if (CoInitialize(nullptr) != S_OK) {
        cerr << "Unable to CoInitialize" << endl;
    }

    const CLSID CLSID_MMDeviceEnumerator = __uuidof(MMDeviceEnumerator);
    const IID IID_IMMDeviceEnumerator = __uuidof(IMMDeviceEnumerator);

    IMMDeviceEnumerator* enumerator = NULL;
    if (FAILED(CoCreateInstance(CLSID_MMDeviceEnumerator, NULL, CLSCTX_ALL, IID_IMMDeviceEnumerator, (void**)&enumerator))) {
        cerr << "Unable to create device enumerator, more will fail later :(" << endl;
    }
    pEnumerator = enumerator;
    pDeviceListener = make_unique<DeviceListener>(*this, pEnumerator);
    cout << "Device enumerator created!" << endl;
    InitDevices();
}

void SndCtrl::InitDevices()
{
    auto pDevices = EnumAudioEndpoints(*pEnumerator);
    auto count = GetCount(*pDevices);
    for (UINT idx = 0; idx < count; idx++) {
        auto pDevice = DeviceFromCollection(*pDevices, idx);
        DeviceAdded(pDevice);
    }

    for (int dataflow = eRender; dataflow < eAll; dataflow++) {
        EDataFlow df = (EDataFlow)dataflow;
        for (int role = 0; role < ERole_enum_count; role++) {
            CComPtr<IMMDevice> pDevice = nullptr;
            ERole rl = (ERole)role;
            pEnumerator->GetDefaultAudioEndpoint(df, rl, &pDevice);

            if (pDevice) {
                LPWSTR id = nullptr;
                pDevice->GetId(&id);

                co_ptr<WCHAR> pId(id);
                SetDefaultDevice(id, df, rl);
            }
        }
    }
}

void SndCtrl::DeviceAdded(CComPtr<IMMDevice> pDevice)
{
    auto nameAndId = DeviceNameId(*pDevice);
    wstring deviceId(nameAndId.id.get());

    float volume = 0;
    BOOL muted = 0;
    auto volumeCtrl = GetVolumeControl(*pDevice);
    volumeCtrl->GetMasterVolumeLevelScalar(&volume);
    volumeCtrl->GetMute(&muted);

    JThread thread;
    auto jObj = jni.CallObject("deviceAdded", "(Ljava/lang/String;Ljava/lang/String;FZI)Lcom/getpcpanel/cpp/AudioDevice;",
        thread.jstr(nameAndId.name.get()), thread.jstr(nameAndId.id.get()), volume, muted, getDataFlow(*pDevice)
    );
    devices.insert({ deviceId, make_unique<AudioDevice>(deviceId, pDevice, jObj)});
}

void SndCtrl::DeviceRemoved(wstring pDevice)
{
    devices.erase(pDevice);

    JThread thread;
    auto jObj = jni.CallObject("deviceRemoved", "(Ljava/lang/String;)V",
        thread.jstr(pDevice.c_str())
    );
}

void SndCtrl::SetDefaultDevice(wstring id, EDataFlow dataFlow, ERole role)
{
    JThread thread;
    auto jObj = jni.CallObject("setDefaultDevice", "(Ljava/lang/String;II)V",
        thread.jstr(id.c_str()), dataFlow, role
    );
}

CComPtr<IMMDeviceCollection> SndCtrl::EnumAudioEndpoints(IMMDeviceEnumerator& enumerator)
{
    IMMDeviceCollection* pDeviceCol = NULL;
    enumerator.EnumAudioEndpoints(eAll, DEVICE_STATE_ACTIVE, &pDeviceCol);
    return CComPtr<IMMDeviceCollection>(pDeviceCol);
}

UINT SndCtrl::GetCount(IMMDeviceCollection& collection)
{
    UINT count;
    collection.GetCount(&count);
    return count;;
}

CComPtr<IMMDevice> SndCtrl::DeviceFromCollection(IMMDeviceCollection& collection, UINT idx)
{
    CComPtr<IMMDevice> pDevice;
    collection.Item(idx, &pDevice);
    return pDevice;
}

SDeviceNameId SndCtrl::DeviceNameId(IMMDevice& device)
{
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

CComPtr<IAudioEndpointVolume> SndCtrl::GetVolumeControl(IMMDevice& device)
{
    CComPtr<IAudioEndpointVolume> pVol;
    device.Activate(__uuidof(IAudioEndpointVolume), CLSCTX_ALL, NULL, (void**)&pVol);
    return pVol;
}

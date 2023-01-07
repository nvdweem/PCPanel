#pragma once
#include "pch.h"
#include "winstring.h"

// https://github.com/File-New-Project/EarTrumpet/blob/master/EarTrumpet/Interop/MMDeviceAPI/IAudioPolicyConfigFactoryVariantFor21H2.cs

interface DECLSPEC_UUID("ab3d4648-e242-459f-b02f-541c70306324") IAudioPolicyConfigFactory;
interface DECLSPEC_UUID("2a59116d-6c4f-45e0-a74f-707e3fef9258") IAudioPolicyConfigFactoryLegacy;
interface IAudioPolicyConfigFactory
{
public:
    virtual HRESULT STDMETHODCALLTYPE __incomplete__QueryInterface();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__AddRef();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__Release();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetIids();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetRuntimeClassName();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetTrustLevel();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__add_CtxVolumeChange();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__remove_CtxVolumeChanged();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__add_RingerVibrateStateChanged();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__remove_RingerVibrateStateChange();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__SetVolumeGroupGainForId();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetVolumeGroupGainForId();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetActiveVolumeGroupForEndpointId();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetVolumeGroupsForEndpoint();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetCurrentVolumeContext();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__SetVolumeGroupMuteForId();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetVolumeGroupMuteForId();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__SetRingerVibrateState();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetRingerVibrateState();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__SetPreferredChatApplication();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__ResetPreferredChatApplication();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetPreferredChatApplication();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__GetCurrentChatApplications();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__add_ChatContextChanged();
    virtual HRESULT STDMETHODCALLTYPE __incomplete__remove_ChatContextChanged();

    virtual HRESULT STDMETHODCALLTYPE SetPersistedDefaultAudioEndpoint(UINT processId, EDataFlow flow, ERole role, HSTRING deviceId);
    virtual HRESULT STDMETHODCALLTYPE GetPersistedDefaultAudioEndpoint(UINT processId, EDataFlow flow, int role, _Outptr_result_maybenull_ _Result_nullonfailure_ HSTRING* string);
    virtual HRESULT STDMETHODCALLTYPE ClearAllPersistedApplicationDefaultEndpoints();
};

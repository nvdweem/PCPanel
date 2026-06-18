#pragma once
#include "pch.h"

// This header uses the legacy SAL1 annotation __in, which MinGW does not ship
// (it only has the SAL2 spelling _In_). Define it away locally — but NOT
// globally: libstdc++ uses `__in` as an ordinary parameter name, so the macro
// must be scoped to this header only and restored at the bottom. (pch.h, and
// therefore every standard header, is already included by this point.)
#ifndef _MSC_VER
#pragma push_macro("__in")
#undef __in
#define __in
#endif

// ----------------------------------------------------------------------------
// PolicyConfig.h
// Undocumented COM-interface IPolicyConfig.
// Use for set default audio render endpoint
// @author EreTIk
// ----------------------------------------------------------------------------

interface DECLSPEC_UUID("f8679f50-850a-41cf-9c72-430f290290c8")
    IPolicyConfig;
class DECLSPEC_UUID("870af99c-171d-4f9e-af0d-e63df40c2bc9")
    CPolicyConfigClient;
// ----------------------------------------------------------------------------
// class CPolicyConfigClient
// {870af99c-171d-4f9e-af0d-e63df40c2bc9}
//  
// interface IPolicyConfig
// {f8679f50-850a-41cf-9c72-430f290290c8}
//
// Query interface:
// CComPtr<IPolicyConfig> PolicyConfig;
// PolicyConfig.CoCreateInstance(__uuidof(CPolicyConfigClient));
// 
// @compatible: Windows 7 and Later
// ----------------------------------------------------------------------------
interface IPolicyConfig : public IUnknown
{
public:

    virtual HRESULT GetMixFormat(
        PCWSTR,
        WAVEFORMATEX**
    );

    virtual HRESULT STDMETHODCALLTYPE GetDeviceFormat(
        PCWSTR,
        INT,
        WAVEFORMATEX**
    );

    virtual HRESULT STDMETHODCALLTYPE ResetDeviceFormat(
        PCWSTR
    );

    virtual HRESULT STDMETHODCALLTYPE SetDeviceFormat(
        PCWSTR,
        WAVEFORMATEX*,
        WAVEFORMATEX*
    );

    virtual HRESULT STDMETHODCALLTYPE GetProcessingPeriod(
        PCWSTR,
        INT,
        PINT64,
        PINT64
    );

    virtual HRESULT STDMETHODCALLTYPE SetProcessingPeriod(
        PCWSTR,
        PINT64
    );

    virtual HRESULT STDMETHODCALLTYPE GetShareMode(
        PCWSTR,
        struct DeviceShareMode*
    );

    virtual HRESULT STDMETHODCALLTYPE SetShareMode(
        PCWSTR,
        struct DeviceShareMode*
    );

    virtual HRESULT STDMETHODCALLTYPE GetPropertyValue(
        PCWSTR,
        const PROPERTYKEY&,
        PROPVARIANT*
    );

    virtual HRESULT STDMETHODCALLTYPE SetPropertyValue(
        PCWSTR,
        const PROPERTYKEY&,
        PROPVARIANT*
    );

    virtual HRESULT STDMETHODCALLTYPE SetDefaultEndpoint(
        __in PCWSTR wszDeviceId,
        __in ERole eRole
    );

    virtual HRESULT STDMETHODCALLTYPE SetEndpointVisibility(
        PCWSTR,
        INT
    );
};

interface DECLSPEC_UUID("568b9108-44bf-40b4-9006-86afe5b5a620")
    IPolicyConfigVista;
class DECLSPEC_UUID("294935CE-F637-4E7C-A41B-AB255460B862")
    CPolicyConfigVistaClient;
// ----------------------------------------------------------------------------
// class CPolicyConfigVistaClient
// {294935CE-F637-4E7C-A41B-AB255460B862}
//  
// interface IPolicyConfigVista
// {568b9108-44bf-40b4-9006-86afe5b5a620}
//
// Query interface:
// CComPtr<IPolicyConfigVista> PolicyConfig;
// PolicyConfig.CoCreateInstance(__uuidof(CPolicyConfigVistaClient));
// 
// @compatible: Windows Vista and Later
// ----------------------------------------------------------------------------
interface IPolicyConfigVista : public IUnknown
{
public:

    virtual HRESULT GetMixFormat(
        PCWSTR,
        WAVEFORMATEX**
    );  // not available on Windows 7, use method from IPolicyConfig

    virtual HRESULT STDMETHODCALLTYPE GetDeviceFormat(
        PCWSTR,
        INT,
        WAVEFORMATEX**
    );

    virtual HRESULT STDMETHODCALLTYPE SetDeviceFormat(
        PCWSTR,
        WAVEFORMATEX*,
        WAVEFORMATEX*
    );

    virtual HRESULT STDMETHODCALLTYPE GetProcessingPeriod(
        PCWSTR,
        INT,
        PINT64,
        PINT64
    );  // not available on Windows 7, use method from IPolicyConfig

    virtual HRESULT STDMETHODCALLTYPE SetProcessingPeriod(
        PCWSTR,
        PINT64
    );  // not available on Windows 7, use method from IPolicyConfig

    virtual HRESULT STDMETHODCALLTYPE GetShareMode(
        PCWSTR,
        struct DeviceShareMode*
    );  // not available on Windows 7, use method from IPolicyConfig

    virtual HRESULT STDMETHODCALLTYPE SetShareMode(
        PCWSTR,
        struct DeviceShareMode*
    );  // not available on Windows 7, use method from IPolicyConfig

    virtual HRESULT STDMETHODCALLTYPE GetPropertyValue(
        PCWSTR,
        const PROPERTYKEY&,
        PROPVARIANT*
    );

    virtual HRESULT STDMETHODCALLTYPE SetPropertyValue(
        PCWSTR,
        const PROPERTYKEY&,
        PROPVARIANT*
    );

    virtual HRESULT STDMETHODCALLTYPE SetDefaultEndpoint(
        __in PCWSTR wszDeviceId,
        __in ERole eRole
    );

    virtual HRESULT STDMETHODCALLTYPE SetEndpointVisibility(
        PCWSTR,
        INT
    );  // not available on Windows 7, use method from IPolicyConfig
};

#if defined(__MINGW32__)
// MSVC reads these IIDs/CLSIDs from the DECLSPEC_UUID above; GCC/MinGW does not,
// so associate them explicitly to make __uuidof(...) resolve.
__CRT_UUID_DECL(IPolicyConfig,            0xf8679f50, 0x850a, 0x41cf, 0x9c, 0x72, 0x43, 0x0f, 0x29, 0x02, 0x90, 0xc8)
__CRT_UUID_DECL(CPolicyConfigClient,      0x870af99c, 0x171d, 0x4f9e, 0xaf, 0x0d, 0xe6, 0x3d, 0xf4, 0x0c, 0x2b, 0xc9)
__CRT_UUID_DECL(IPolicyConfigVista,       0x568b9108, 0x44bf, 0x40b4, 0x90, 0x06, 0x86, 0xaf, 0xe5, 0xb5, 0xa6, 0x20)
__CRT_UUID_DECL(CPolicyConfigVistaClient, 0x294935ce, 0xf637, 0x4e7c, 0xa4, 0x1b, 0xab, 0x25, 0x54, 0x60, 0xb8, 0x62)
#endif

#ifndef _MSC_VER
#pragma pop_macro("__in")
#endif

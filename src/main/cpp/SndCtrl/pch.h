// pch.h: This is a precompiled header file.
// Files listed below are compiled only once, improving build performance for future builds.
// This also affects IntelliSense performance, including code completion and many code browsing features.
// However, files listed here are ALL re-compiled if any one of them is updated between builds.
// Do not add files here that you will be updating frequently as this negates the performance advantage.

#ifndef PCH_H
#define PCH_H

// add headers that you want to pre-compile here
#include "framework.h"
#include <tchar.h>   // _T / TCHAR (transitively included by MSVC's headers)

// Some COM headers below (policyconfig.h, audiopolicyconfigfactory.h) use the
// `interface` keyword. MSVC and the Windows SDK define it; make sure it exists
// for MinGW-w64 / GCC too.
#ifndef interface
#define interface struct
#endif

// CComPtr / CComQIPtr used to come from ATL (<atlbase.h>), which ships only
// with Visual Studio. comptr_compat.h is a portable replacement so the DLL can
// be built without Visual Studio (and cross-compiled on Linux). See cpp/README.md.
#include "comptr_compat.h"
#include <jni.h>

#include <audioclient.h>
#include <audiopolicy.h>
#include <endpointvolume.h>
#include <functional>
#include <iostream>
// Standard containers/utilities used across the project. MSVC pulled these in
// transitively; MinGW/libstdc++ does not, so include them explicitly.
#include <list>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>
#include <mmdeviceapi.h>

#include <functiondiscoverykeys_devpkey.h> // Must be after mmdeviceapi

using namespace std;

#endif //PCH_H

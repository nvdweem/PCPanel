// dllmain.cpp : Defines the entry point for the DLL application.
#include "pch.h"
#include "JniCaller.h"
#include "sndctrl.h"

BOOL APIENTRY DllMain( HMODULE hModule,
                       DWORD  ul_reason_for_call,
                       LPVOID lpReserved
                     )
{
    switch (ul_reason_for_call)
    {
    case DLL_PROCESS_ATTACH:
    case DLL_PROCESS_DETACH:
    case DLL_THREAD_ATTACH:
    case DLL_THREAD_DETACH:
        break;
    }
    return TRUE;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    pJvm = vm;
    return JNI_VERSION_10;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
    pSndCtrl.release();
}

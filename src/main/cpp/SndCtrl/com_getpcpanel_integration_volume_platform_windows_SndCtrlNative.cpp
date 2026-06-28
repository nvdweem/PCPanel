#include "pch.h"
#include "com_getpcpanel_integration_volume_platform_windows_SndCtrlNative.h"
#include "JniCaller.h"
#include "sndctrl.h"
#include "helpers.h"
#include <iostream>
#include <psapi.h>
#include <set>
#include <windows.h>
#include <string>
#include <thread>

std::wstring str(JNIEnv* env, jstring string) {
    std::wstring value;
    if (string == NULL) {
        return value; // empty string
    }
    const jchar* raw = env->GetStringChars(string, NULL);
    if (raw != NULL) {
        jsize len = env->GetStringLength(string);
        value.assign(raw, raw + len);
        env->ReleaseStringChars(string, raw);
    }
    return value;
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    start
 * Signature: (Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_start(JNIEnv* env, jclass, jobject obj) {
    // Build SndCtrl on a dedicated thread so this JNI call returns immediately. Its constructor
    // enumerates every audio endpoint (InitDevices) with synchronous COM calls that can block
    // indefinitely on an endpoint that is slow or stuck re-initialising (e.g. a virtual device right
    // after the machine resumes from sleep). Running that inline would block the @PostConstruct of the
    // @ApplicationScoped ISndCtrl bean, so a stuck endpoint would hang every thread that touches audio
    // (via Arc's bean-creation lock) and drain the web worker pool. Off-thread, a stuck endpoint only
    // delays the audio thread.
    //
    // The thread owns the COM apartment for the process lifetime and pumps its message queue, which
    // STA COM (CoInitialize in SndCtrl's constructor) needs to deliver endpoint/session change
    // notifications. obj is promoted to a global ref because the local ref is only valid on this
    // returning frame; SndCtrl takes its own global ref, so we drop ours afterwards.
    jobject globalObj = env->NewGlobalRef(obj);
    std::thread([globalObj]() {
        JThread thread; // attaches this thread to the JVM for its whole lifetime
        if (*thread) {
            pSndCtrl = make_unique<SndCtrl>(thread.raw(), globalObj);
            thread->DeleteGlobalRef(globalObj);
        }
        MSG msg;
        while (GetMessage(&msg, nullptr, 0, 0) > 0) {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }
    }).detach();
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    setDeviceVolume
 * Signature: (Ljava/lang/String;F)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_setDeviceVolume(JNIEnv* env, jobject, jstring jDeviceId, jfloat volume) {
    //cout << "Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_setDeviceVolume" << endl;
    if (!pSndCtrl) return;
    auto deviceId = str(env, jDeviceId);
    pSndCtrl->SetDeviceVolume(deviceId, volume);
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    setProcessVolume
 * Signature: (Ljava/lang/String;IF)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_setProcessVolume(JNIEnv* env, jobject, jstring jDeviceId, jint pid, jfloat volume) {
    //cout << "Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_setProcessVolume" << endl;
    if (!pSndCtrl) return;
    auto deviceId = str(env, jDeviceId);
    pSndCtrl->SetProcessVolume(deviceId, pid, volume);
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    setFocusVolume
 * Signature: (F)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_setFocusVolume(JNIEnv*, jobject, jfloat volume) {
    //cout << "Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_setFocusVolume" << endl;
    if (!pSndCtrl) return;
    pSndCtrl->SetFocusVolume(volume);
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    setDefaultDevice
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_setDefaultDevice(JNIEnv* env, jobject, jstring jDevice, jint dataFlow, jint role) {
    //cout << "Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_setDefaultDevice" << endl;
    if (!pSndCtrl) return;
    auto device = str(env, jDevice);
    pSndCtrl->UpdateDefaultDevice(device, (EDataFlow) dataFlow, (ERole) role);
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    muteDevice
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_muteDevice(JNIEnv* env, jobject, jstring jDevice, jboolean muted) {
    //cout << "Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_muteDevice" << endl;
    if (!pSndCtrl) return;
    auto device = str(env, jDevice);
    pSndCtrl->MuteDevice(device, muted);
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    muteSession
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_muteSession(JNIEnv* env, jobject, jstring jDevice, jint pid, jboolean muted) {
    //cout << "Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_muteSession" << endl;
    if (!pSndCtrl) return;
    auto device = str(env, jDevice);
    pSndCtrl->MuteProcess(device, pid, muted);
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    getFocusApplication
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_getFocusApplication(JNIEnv* env, jobject) {
    //cout << "Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_getFocusApplication" << endl;
    auto pid = GetFocusProcessId();
    auto name = GetProcessName(pid);
    return env->NewString((jchar*) name.c_str(), (jsize) name.length());
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    setPersistedDefaultAudioEndpoint
 * Signature: (IILjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_setPersistedDefaultAudioEndpoint(JNIEnv* env, jobject, jint pid, jint flow, jstring jDeviceId) {
    if (!pSndCtrl) return false;
    auto device = str(env, jDeviceId);
    return pSndCtrl->SetPersistedDefaultAudioEndpoint(pid, (EDataFlow) flow, device);
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    getPersistedDefaultAudioEndpoint
 * Signature: (II)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_getPersistedDefaultAudioEndpoint(JNIEnv* env, jobject, jint pid, jint flow) {
    if (!pSndCtrl) return env->NewString((jchar*) L"", 0);
    auto result = pSndCtrl->GetPersistedDefaultAudioEndpoint(pid, (EDataFlow) flow);
    return env->NewString((jchar*) result.c_str(), (jsize)result.length());
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    hasAudioPolicyConfigFactory
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_hasAudioPolicyConfigFactory(JNIEnv*, jobject) {
    if (!pSndCtrl) return false;
    return pSndCtrl->HasAudioPolicyConfigFactory();
}

/*
 * Class:     com_getpcpanel_integration_volume_platform_windows_SndCtrlNative
 * Method:    triggerAv
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_getpcpanel_integration_volume_platform_windows_SndCtrlNative_triggerAv(JNIEnv*, jobject) {
    new std::thread([](){
        Sleep(1000);
        if (pSndCtrl) pSndCtrl->TriggerAv();
    });
}
